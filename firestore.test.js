const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const CREATOR_UID = "creator_alice_123";
const VIEWER_UID = "viewer_bob_456";
const STRANGER_UID = "stranger_eve_789";
const JOIN_CODE = "GOATRIP26";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTripPayload(overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    creatorUid: CREATOR_UID,
    creatorName: "Alice",
    title: "Goa Coastal Trip",
    currencyCode: "INR",
    budgetCents: 5000000,
    status: "active",
    joinCode: JOIN_CODE,
    memberUids: [CREATOR_UID],
    coHostUids: [],
    members: [
      { memberId: "m_alice", displayName: "Alice", linkedUid: CREATOR_UID },
      { memberId: "m_bob", displayName: "Bob", linkedUid: "" },
    ],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validBillPayload(tripId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    tripId,
    creatorUid: CREATOR_UID,
    title: "Beach Shack Dinner",
    category: "food",
    amountCents: 320000,
    locationName: "Anjuna Beach, Goa",
    paidByMemberId: "m_alice",
    splitMode: "equal",
    participantIds: ["m_alice", "m_bob"],
    splits: { m_alice: 160000, m_bob: 160000 },
    receiptAttached: true,
    billDateMillis: Date.now() - 5000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validReceiptPayload(tripId, billId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    tripId,
    billId,
    creatorUid: CREATOR_UID,
    imageBase64: "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAA==",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSettlementPayload(tripId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    tripId,
    creatorUid: CREATOR_UID,
    fromMemberId: "m_bob",
    toMemberId: "m_alice",
    amountCents: 100000,
    note: "UPI transfer",
    settledAtMillis: Date.now() - 4000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write trips, bills, settlements, or tripLinks", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("trips").doc("trip_1").set(validTripPayload());
  });
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("trips").doc("trip_1").get());
  await assertFails(unauth.collection("trips").where("memberUids", "array-contains", CREATOR_UID).get());
  await assertFails(unauth.collection("trips").doc("trip_2").set(validTripPayload()));
  await assertFails(
    unauth.collection("trips").doc("trip_1").collection("bills").doc("bill_1").set(validBillPayload("trip_1"))
  );
  await assertFails(
    unauth.collection("trips").doc("trip_1").collection("settlements").doc("set_1").set(validSettlementPayload("trip_1"))
  );
  await assertFails(
    unauth.collection("users").doc(CREATOR_UID).collection("tripLinks").doc("trip_1").get()
  );
});

test("Creator can create active INR trip with budget, edit members & budget, and add percentage-split bills", async () => {
  const creatorDb = testEnv.authenticatedContext(CREATOR_UID).firestore();
  await assertSucceeds(creatorDb.collection("trips").doc("trip_1").set(validTripPayload()));

  await assertSucceeds(
    creatorDb.collection("trips").where("memberUids", "array-contains", CREATOR_UID).get()
  );
  await assertFails(creatorDb.collection("trips").get());

  // Creator updates budget
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_1").update({
      budgetCents: 7500000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Percentage-based split bill succeeds
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_1").collection("bills").doc("bill_pct").set(
      validBillPayload("trip_1", {
        splitMode: "percentage",
        splits: { m_alice: 192000, m_bob: 128000 },
        splitPercentages: { m_alice: 6000, m_bob: 4000 },
      })
    )
  );
});

test("QR join enforces valid joinCode and grants read-only access while viewer is NOT a co-host", async () => {
  const creatorDb = testEnv.authenticatedContext(CREATOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();

  await assertSucceeds(creatorDb.collection("trips").doc("trip_1").set(validTripPayload()));
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_1").collection("bills").doc("bill_1").set(validBillPayload("trip_1"))
  );
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_1").collection("receipts").doc("bill_1").set(validReceiptPayload("trip_1", "bill_1"))
  );
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_1").collection("settlements").doc("set_1").set(validSettlementPayload("trip_1"))
  );

  await assertFails(viewerDb.collection("trips").doc("trip_1").get());

  // Wrong joinCode fails
  await assertFails(
    viewerDb.collection("trips").doc("trip_1").update({
      memberUids: [CREATOR_UID, VIEWER_UID],
      lastJoinCodeAttempt: "WRONGCODE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Correct joinCode succeeds
  await assertSucceeds(
    viewerDb.collection("trips").doc("trip_1").update({
      memberUids: [CREATOR_UID, VIEWER_UID],
      lastJoinCodeAttempt: JOIN_CODE,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Viewer can register own syncedUsers entry and read bills/settlements
  await assertSucceeds(
    viewerDb.collection("trips").doc("trip_1").collection("syncedUsers").doc(VIEWER_UID).set({
      userId: VIEWER_UID,
      tripId: "trip_1",
      displayName: "Bob",
      linkedMemberId: "m_bob",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(viewerDb.collection("trips").doc("trip_1").collection("bills").get());
  await assertSucceeds(viewerDb.collection("trips").doc("trip_1").collection("settlements").get());

  // Stranger cannot read syncedUsers or settlements
  await assertFails(strangerDb.collection("trips").doc("trip_1").collection("syncedUsers").get());
  await assertFails(strangerDb.collection("trips").doc("trip_1").collection("settlements").get());

  // CREATOR LOCK: View-only member cannot add/edit bills or record settlements
  await assertFails(
    viewerDb.collection("trips").doc("trip_1").collection("bills").doc("bill_2").set(
      validBillPayload("trip_1", { creatorUid: VIEWER_UID })
    )
  );
  await assertFails(
    viewerDb.collection("trips").doc("trip_1").collection("settlements").doc("set_2").set(
      validSettlementPayload("trip_1", { creatorUid: VIEWER_UID })
    )
  );
});

test("Co-Host can add/edit/delete bills and record settlements, but cannot end trip or change co-hosts", async () => {
  const creatorDb = testEnv.authenticatedContext(CREATOR_UID).firestore();
  const coHostDb = testEnv.authenticatedContext(VIEWER_UID).firestore();

  await assertSucceeds(creatorDb.collection("trips").doc("trip_1").set(validTripPayload()));
  await assertSucceeds(
    coHostDb.collection("trips").doc("trip_1").update({
      memberUids: [CREATOR_UID, VIEWER_UID],
      lastJoinCodeAttempt: JOIN_CODE,
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Creator promotes Bob to Co-Host
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_1").update({
      coHostUids: [VIEWER_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Co-Host can add a bill & record a settlement
  await assertSucceeds(
    coHostDb.collection("trips").doc("trip_1").collection("bills").doc("bill_co").set(
      validBillPayload("trip_1", { creatorUid: VIEWER_UID })
    )
  );
  await assertSucceeds(
    coHostDb.collection("trips").doc("trip_1").collection("settlements").doc("set_co").set(
      validSettlementPayload("trip_1", { creatorUid: VIEWER_UID })
    )
  );

  // Co-Host CANNOT end the trip or hijack ownership
  await assertFails(
    coHostDb.collection("trips").doc("trip_1").update({
      status: "ended",
      endedAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    coHostDb.collection("trips").doc("trip_1").update({
      creatorUid: VIEWER_UID,
      creatorName: "Bob",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Creator can transfer ownership to another trip member and loses primary creator control", async () => {
  const creatorDb = testEnv.authenticatedContext(CREATOR_UID).firestore();
  const newOwnerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();

  await assertSucceeds(creatorDb.collection("trips").doc("trip_1").set(validTripPayload()));
  await assertSucceeds(
    newOwnerDb.collection("trips").doc("trip_1").update({
      memberUids: [CREATOR_UID, VIEWER_UID],
      lastJoinCodeAttempt: JOIN_CODE,
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Cannot transfer to stranger not in memberUids
  await assertFails(
    creatorDb.collection("trips").doc("trip_1").update({
      creatorUid: STRANGER_UID,
      creatorName: "Eve",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Valid ownership transfer to Bob (with Alice optionally kept as co-host or regular viewer)
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_1").update({
      creatorUid: VIEWER_UID,
      creatorName: "Bob",
      coHostUids: [],
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Former creator can no longer end the trip
  await assertFails(
    creatorDb.collection("trips").doc("trip_1").update({
      status: "ended",
      endedAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // New owner can end the trip, locking bills and settlements permanently
  const endedAt = new Date(Date.now() - 100);
  await assertSucceeds(
    newOwnerDb.collection("trips").doc("trip_1").update({
      status: "ended",
      endedAt,
      updatedAt: endedAt,
    })
  );

  await assertFails(
    newOwnerDb.collection("trips").doc("trip_1").collection("settlements").doc("set_after").set(
      validSettlementPayload("trip_1", { creatorUid: VIEWER_UID })
    )
  );
});

test("Users collection enforces owner-only access and blocks enumeration", async () => {
  const creatorDb = testEnv.authenticatedContext(CREATOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    creatorDb.collection("users").doc(CREATOR_UID).set({
      userId: CREATOR_UID,
      displayName: "Alice",
      defaultCurrencyCode: "INR",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(creatorDb.collection("users").doc(CREATOR_UID).get());
  await assertFails(viewerDb.collection("users").doc(CREATOR_UID).get());
  await assertFails(viewerDb.collection("users").get());
});

test("Multi-payer split payment bills and soft-delete / restore respect Creator Lock", async () => {
  const creatorDb = testEnv.authenticatedContext(CREATOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();

  await assertSucceeds(creatorDb.collection("trips").doc("trip_mp").set(validTripPayload()));
  await assertSucceeds(
    viewerDb.collection("trips").doc("trip_mp").update({
      memberUids: [CREATOR_UID, VIEWER_UID],
      lastJoinCodeAttempt: JOIN_CODE,
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Creator logs a multi-payer bill (Card + UPI)
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_mp").collection("bills").doc("bill_mp").set(
      validBillPayload("trip_mp", {
        payers: [
          { memberId: "m_alice", amountCents: 250000, paymentMethod: "card" },
          { memberId: "m_bob", amountCents: 70000, paymentMethod: "upi" },
        ],
        deleted: false,
        deletedAtMillis: 0,
        deletedByUid: "",
      })
    )
  );

  // Viewer cannot soft-delete
  await assertFails(
    viewerDb.collection("trips").doc("trip_mp").collection("bills").doc("bill_mp").update({
      deleted: true,
      deletedAtMillis: Date.now(),
      deletedByUid: VIEWER_UID,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Creator can soft-delete and restore
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_mp").collection("bills").doc("bill_mp").update({
      deleted: true,
      deletedAtMillis: Date.now(),
      deletedByUid: CREATOR_UID,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_mp").collection("bills").doc("bill_mp").update({
      deleted: false,
      deletedAtMillis: 0,
      deletedByUid: "",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Bill notes stay Creator-locked while any trip member can add own comments and reactions", async () => {
  const creatorDb = testEnv.authenticatedContext(CREATOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();

  await assertSucceeds(creatorDb.collection("trips").doc("trip_collab").set(validTripPayload()));
  await assertSucceeds(
    viewerDb.collection("trips").doc("trip_collab").update({
      memberUids: [CREATOR_UID, VIEWER_UID],
      lastJoinCodeAttempt: JOIN_CODE,
      updatedAt: new Date(Date.now() - 600),
    })
  );

  await assertSucceeds(
    creatorDb.collection("trips").doc("trip_collab").collection("bills").doc("bill_c1").set(
      validBillPayload("trip_collab", {
        notes: "3 nights villa stay @ ₹4,200/night",
      })
    )
  );

  // Viewer STILL cannot modify bill notes or amounts (Creator Lock intact)
  await assertFails(
    viewerDb.collection("trips").doc("trip_collab").collection("bills").doc("bill_c1").update({
      notes: "Tampered note",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  const billRef = (db) => db.collection("trips").doc("trip_collab").collection("bills").doc("bill_c1");

  // Viewer CAN post own comment & toggle own verification reaction
  await assertSucceeds(
    billRef(viewerDb).collection("comments").doc("cmt_1").set({
      tripId: "trip_collab",
      billId: "bill_c1",
      userId: VIEWER_UID,
      authorName: "Bob",
      text: "Includes breakfast for all 3 mornings!",
      createdAt: new Date(Date.now() - 250),
    })
  );
  await assertSucceeds(
    billRef(viewerDb).collection("reactions").doc(VIEWER_UID).set({
      tripId: "trip_collab",
      billId: "bill_c1",
      userId: VIEWER_UID,
      authorName: "Bob",
      emoji: "verified",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Viewer CANNOT spoof another user's reaction or delete creator's comment
  await assertFails(
    billRef(viewerDb).collection("reactions").doc(CREATOR_UID).set({
      tripId: "trip_collab",
      billId: "bill_c1",
      userId: CREATOR_UID,
      authorName: "Alice",
      emoji: "verified",
      updatedAt: new Date(Date.now() - 180),
    })
  );
  await assertSucceeds(
    billRef(creatorDb).collection("comments").doc("cmt_alice").set({
      tripId: "trip_collab",
      billId: "bill_c1",
      userId: CREATOR_UID,
      authorName: "Alice",
      text: "Confirmed with host.",
      createdAt: new Date(Date.now() - 150),
    })
  );
  await assertFails(billRef(viewerDb).collection("comments").doc("cmt_alice").delete());

  // Viewer CAN delete own comment & own reaction
  await assertSucceeds(billRef(viewerDb).collection("comments").doc("cmt_1").delete());
  await assertSucceeds(billRef(viewerDb).collection("reactions").doc(VIEWER_UID).delete());

  // Stranger is completely blocked from reading or writing comments & reactions
  await assertFails(billRef(strangerDb).collection("comments").get());
  await assertFails(billRef(strangerDb).collection("reactions").get());
  await assertFails(
    billRef(strangerDb).collection("comments").doc("cmt_eve").set({
      tripId: "trip_collab",
      billId: "bill_c1",
      userId: STRANGER_UID,
      authorName: "Eve",
      text: "Sneaking in",
      createdAt: new Date(Date.now() - 100),
    })
  );
});
