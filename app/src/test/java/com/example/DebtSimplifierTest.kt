package com.example

import com.example.data.model.Bill
import com.example.data.model.BillCategory
import com.example.data.model.BillPayer
import com.example.data.model.MultiDayTemplate
import com.example.data.model.PaymentMethod
import com.example.data.model.ReactionType
import com.example.data.model.SettlementRecord
import com.example.data.model.SplitMode
import com.example.data.model.Trip
import com.example.data.model.TripMember
import com.example.domain.CurrencyFormatter
import com.example.domain.DebtSimplifier
import com.example.domain.DuplicateExpenseDetector
import com.example.domain.GitHubReleaseUpdater
import com.example.domain.ShareIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtSimplifierTest {

    private fun sampleTrip(budgetCents: Long = 500_000L): Trip = Trip(
        id = "trip_goa",
        creatorUid = "uid_alice",
        creatorName = "Alice",
        title = "Goa Trip",
        currencyCode = "INR",
        budgetCents = budgetCents,
        members = listOf(
            TripMember("m_alice", "Alice", "uid_alice"),
            TripMember("m_bob", "Bob", "uid_bob"),
            TripMember("m_cara", "Cara", "")
        )
    )

    @Test
    fun computeEqualSplits_distributesRemainderExactly() {
        val splits = DebtSimplifier.computeEqualSplits(100_00L, listOf("m_alice", "m_bob", "m_cara"))
        assertEquals(100_00L, splits.values.sum())
        assertEquals(3334L, splits["m_alice"])
        assertEquals(3333L, splits["m_bob"])
        assertEquals(3333L, splits["m_cara"])
    }

    @Test
    fun computePercentageSplits_handlesOddTotalsWithoutRoundingDrift() {
        val bps = mapOf(
            "m_alice" to 5000L, // 50%
            "m_bob" to 3333L,   // 33.33%
            "m_cara" to 1667L   // 16.67%
        )
        val splits = DebtSimplifier.computePercentageSplits(100_01L, bps)
        assertEquals(100_01L, splits.values.sum())
        assertEquals(5001L, splits["m_alice"])
        assertEquals(3333L, splits["m_bob"])
        assertEquals(1667L, splits["m_cara"])
    }

    @Test
    fun computeEqualPercentagesBps_sumsTo10000() {
        val bps = DebtSimplifier.computeEqualPercentagesBps(listOf("a", "b", "c"))
        assertEquals(10_000L, bps.values.sum())
    }

    @Test
    fun summarizeTrip_deductsRecordedSettlementsFromBalancesAndTransfers() {
        val trip = sampleTrip()
        val bill = Bill(
            id = "b1",
            tripId = trip.id,
            title = "Shack Dinner",
            category = BillCategory.FOOD.wireValue,
            amountCents = 300_000L, // ₹3,000
            paidByMemberId = "m_alice",
            splitMode = SplitMode.EQUAL.wireValue,
            participantIds = listOf("m_alice", "m_bob", "m_cara"),
            splits = mapOf("m_alice" to 100_000L, "m_bob" to 100_000L, "m_cara" to 100_000L),
            billDateMillis = 1_700_000_000_000L
        )

        // Before settlement: Bob owes Alice ₹1,000, Cara owes Alice ₹1,000
        val before = DebtSimplifier.summarizeTrip(trip, listOf(bill), "uid_alice", null)
        assertEquals(200_000L, before.currentUserBalance?.netBalanceCents)
        assertEquals(2, before.simplifiedTransfers.size)

        // Record Bob paying ₹1,000 and Cara paying partial ₹400
        val settlements = listOf(
            SettlementRecord(
                id = "s1",
                tripId = trip.id,
                fromMemberId = "m_bob",
                toMemberId = "m_alice",
                amountCents = 100_000L,
                settledAtMillis = 1_700_000_100_000L
            ),
            SettlementRecord(
                id = "s2",
                tripId = trip.id,
                fromMemberId = "m_cara",
                toMemberId = "m_alice",
                amountCents = 40_000L,
                settledAtMillis = 1_700_000_200_000L
            )
        )

        val after = DebtSimplifier.summarizeTrip(trip, listOf(bill), "uid_alice", null, settlements)
        assertEquals(140_000L, after.totalSettledCents)
        assertEquals(60_000L, after.currentUserBalance?.netBalanceCents)
        assertEquals(1, after.simplifiedTransfers.size)
        assertEquals("m_cara", after.simplifiedTransfers.first().fromMemberId)
        assertEquals("m_alice", after.simplifiedTransfers.first().toMemberId)
        assertEquals(60_000L, after.simplifiedTransfers.first().amountCents)
    }

    @Test
    fun smartGraphReduction_collapsesTransitiveDebtChain() {
        val trip = sampleTrip()
        // Bill 1: Bob pays ₹500 solely for Alice -> Alice owes Bob ₹500
        val bill1 = Bill(
            id = "b_chain_1",
            tripId = trip.id,
            title = "Coffee",
            amountCents = 50_000L,
            paidByMemberId = "m_bob",
            participantIds = listOf("m_alice"),
            splits = mapOf("m_alice" to 50_000L),
            billDateMillis = 1_700_000_000_000L
        )
        // Bill 2: Cara pays ₹500 solely for Bob -> Bob owes Cara ₹500
        val bill2 = Bill(
            id = "b_chain_2",
            tripId = trip.id,
            title = "Scooter Fuel",
            amountCents = 50_000L,
            paidByMemberId = "m_cara",
            participantIds = listOf("m_bob"),
            splits = mapOf("m_bob" to 50_000L),
            billDateMillis = 1_700_000_100_000L
        )

        val summary = DebtSimplifier.summarizeTrip(trip, listOf(bill1, bill2), "uid_alice", null)

        // Raw pairwise retains both edges (A->B ₹500, B->C ₹500)
        assertEquals(2, summary.rawPairwiseTransfers.size)
        // Graph reduction merges chain into a single transfer: Alice -> Cara ₹500
        assertEquals(1, summary.simplifiedTransfers.size)
        val reduced = summary.simplifiedTransfers.first()
        assertEquals("m_alice", reduced.fromMemberId)
        assertEquals("m_cara", reduced.toMemberId)
        assertEquals(50_000L, reduced.amountCents)
    }

    @Test
    fun multiPayerSplitPayment_creditsEachPayerCorrectly() {
        val trip = Trip(
            id = "trip_blr",
            creatorUid = "uid_amit",
            creatorName = "Amit",
            title = "Coorg",
            members = listOf(
                TripMember("m_amit", "Amit", "uid_amit"),
                TripMember("m_priya", "Priya", "uid_priya"),
                TripMember("m_rohan", "Rohan", ""),
                TripMember("m_neha", "Neha", "")
            )
        )
        // ₹3,200 dinner: Amit paid ₹2,500 via Card, Priya paid ₹700 via UPI; split equally ₹800 each
        val bill = Bill(
            id = "b_multi",
            tripId = trip.id,
            title = "Dineout Dinner",
            amountCents = 320_000L,
            paidByMemberId = "m_amit",
            payers = listOf(
                BillPayer("m_amit", 250_000L, PaymentMethod.CARD.wireValue),
                BillPayer("m_priya", 70_000L, PaymentMethod.UPI.wireValue)
            ),
            participantIds = listOf("m_amit", "m_priya", "m_rohan", "m_neha"),
            splits = mapOf(
                "m_amit" to 80_000L,
                "m_priya" to 80_000L,
                "m_rohan" to 80_000L,
                "m_neha" to 80_000L
            ),
            billDateMillis = 1_700_000_000_000L
        )

        val summary = DebtSimplifier.summarizeTrip(trip, listOf(bill), "uid_amit", null)
        val balancesById = summary.memberBalances.associateBy { it.memberId }

        assertEquals(170_000L, balancesById["m_amit"]?.netBalanceCents)   // +₹1,700
        assertEquals(-10_000L, balancesById["m_priya"]?.netBalanceCents)  // -₹100
        assertEquals(-80_000L, balancesById["m_rohan"]?.netBalanceCents)  // -₹800
        assertEquals(-80_000L, balancesById["m_neha"]?.netBalanceCents)   // -₹800
        assertEquals(320_000L, summary.totalTripSpendCents)
    }

    @Test
    fun softDeletedBills_areExcludedFromTotalsBalancesAndInsights() {
        val trip = sampleTrip()
        val activeBill = Bill(
            id = "b_active",
            tripId = trip.id,
            title = "Breakfast",
            amountCents = 60_000L,
            paidByMemberId = "m_alice",
            participantIds = listOf("m_alice", "m_bob"),
            splits = mapOf("m_alice" to 30_000L, "m_bob" to 30_000L),
            billDateMillis = 1_700_000_000_000L
        )
        val deletedBill = Bill(
            id = "b_deleted",
            tripId = trip.id,
            title = "Accidental Duplicate",
            amountCents = 200_000L,
            paidByMemberId = "m_bob",
            participantIds = listOf("m_alice", "m_bob"),
            splits = mapOf("m_alice" to 100_000L, "m_bob" to 100_000L),
            billDateMillis = 1_700_000_050_000L,
            deleted = true,
            deletedAtMillis = 1_700_000_060_000L
        )

        val summary = DebtSimplifier.summarizeTrip(trip, listOf(activeBill, deletedBill), "uid_alice", null)
        assertEquals(60_000L, summary.totalTripSpendCents)
        assertEquals(1, summary.billCount)
        assertEquals(30_000L, summary.currentUserBalance?.netBalanceCents)

        val insights = DebtSimplifier.computeInsights(trip, listOf(activeBill, deletedBill))
        assertEquals(60_000L, insights.totalSpendCents)

        val groups = DebtSimplifier.groupBillsByDay(listOf(activeBill, deletedBill))
        assertEquals(1, groups.size)
        assertEquals(1, groups.first().bills.size)
    }

    @Test
    fun duplicateExpenseDetector_flagsSameAmountWithinWindowAndIgnoresDeleted() {
        val trip = sampleTrip()
        val baseTime = 1_700_000_000_000L
        val existing = Bill(
            id = "b_dinner",
            tripId = trip.id,
            title = "Dinner",
            category = BillCategory.FOOD.wireValue,
            amountCents = 145_000L, // ₹1,450
            paidByMemberId = "m_alice",
            billDateMillis = baseTime
        )

        // 10 minutes later, Friend B tries to log "Biryani ₹1,450"
        val match = DuplicateExpenseDetector.findPotentialDuplicate(
            trip = trip,
            existingBills = listOf(existing),
            editingBillId = null,
            candidateAmountCents = 145_000L,
            candidateTitle = "Biryani",
            candidateCategory = BillCategory.FOOD,
            candidateDateMillis = baseTime + 10L * 60_000L
        )
        assertNotNull(match)
        assertEquals("b_dinner", match?.existingBill?.id)
        assertEquals(10L, match?.minutesApart)

        // Editing the same bill does not flag itself
        val selfEdit = DuplicateExpenseDetector.findPotentialDuplicate(
            trip = trip,
            existingBills = listOf(existing),
            editingBillId = "b_dinner",
            candidateAmountCents = 145_000L,
            candidateTitle = "Biryani",
            candidateCategory = BillCategory.FOOD,
            candidateDateMillis = baseTime + 10L * 60_000L
        )
        assertNull(selfEdit)

        // Soft-deleted bills are ignored
        val deletedMatch = DuplicateExpenseDetector.findPotentialDuplicate(
            trip = trip,
            existingBills = listOf(existing.copy(deleted = true)),
            editingBillId = null,
            candidateAmountCents = 145_000L,
            candidateTitle = "Biryani",
            candidateCategory = BillCategory.FOOD,
            candidateDateMillis = baseTime + 10L * 60_000L
        )
        assertNull(deletedMatch)
    }

    @Test
    fun shareIntentParser_extractsSwiggyZomatoIrctcAndUberText() {
        val swiggy = ShareIntentParser.parseStructuredText(
            "Swiggy order from Meghana Foods\n1x Chicken Biryani - ₹340\nGrand Total: ₹1,245.50"
        )
        assertEquals("Swiggy", swiggy.sourceApp)
        assertEquals(BillCategory.FOOD, swiggy.category)
        assertEquals(124_550L, swiggy.amountCents)
        assertTrue(swiggy.title.contains("Meghana Foods"))

        val irctc = ShareIntentParser.parseStructuredText(
            "IRCTC e-Ticket PNR 4829102931\nBoarding: Pune Jn\nTotal Fare: Rs. 2,860"
        )
        assertEquals("IRCTC", irctc.sourceApp)
        assertEquals(BillCategory.TRANSPORT, irctc.category)
        assertEquals(286_000L, irctc.amountCents)

        val uber = ShareIntentParser.parseStructuredText(
            "Your Uber trip receipt\nPickup: Goa Airport\nTotal Paid: ₹685.00"
        )
        assertEquals("Uber", uber.sourceApp)
        assertEquals(BillCategory.TRANSPORT, uber.category)
        assertEquals(68_500L, uber.amountCents)
    }

    @Test
    fun githubReleaseUpdater_comparesSemverAndVersionCodeCorrectly() {
        assertTrue(GitHubReleaseUpdater.isVersionNewer("1.2.0", "1.1.9"))
        assertTrue(GitHubReleaseUpdater.isVersionNewer("1.0.10", "1.0.9"))
        assertTrue(GitHubReleaseUpdater.isVersionNewer("2.0", "1.9.9"))
        assertFalse(GitHubReleaseUpdater.isVersionNewer("1.0.0", "1.0.0"))
        assertFalse(GitHubReleaseUpdater.isVersionNewer("1.0.0", "1.1.0"))

        assertTrue(
            GitHubReleaseUpdater.isReleaseNewer(
                remoteVersion = "1.0.2",
                remoteVersionCode = 2,
                currentVersion = "1.0.1",
                currentVersionCode = 1
            )
        )
        assertFalse(
            GitHubReleaseUpdater.isReleaseNewer(
                remoteVersion = "1.0.5",
                remoteVersionCode = 5,
                currentVersion = "1.0.5",
                currentVersionCode = 5
            )
        )
        assertEquals(
            14,
            GitHubReleaseUpdater.extractVersionCode("1.0.14", "Automated release\n- Version code: 14")
        )
    }

    @Test
    fun githubReleaseUpdater_validatesTrustedHostsAndSanitizesApkFilename() {
        assertTrue(GitHubReleaseUpdater.isTrustedAssetDownloadHost("https://objects.githubusercontent.com/github-production-release-asset/foo.apk"))
        assertTrue(GitHubReleaseUpdater.isTrustedAssetDownloadHost("https://github.com/sumeethibare/billi/releases/download/v1.0.2/Billi-v1.0.2.apk"))
        assertFalse(GitHubReleaseUpdater.isTrustedAssetDownloadHost("http://objects.githubusercontent.com/foo.apk"))
        assertFalse(GitHubReleaseUpdater.isTrustedAssetDownloadHost("https://attacker.tld/Billi.apk"))

        assertEquals("Billi-v1.0.7.apk", GitHubReleaseUpdater.sanitizeApkFileName("../../Billi-v1.0.7.apk"))
        assertEquals("evil_name.apk", GitHubReleaseUpdater.sanitizeApkFileName("foo/bar/evil name"))
    }

    @Test
    fun githubReleaseUpdater_extractsAndVerifiesSha256Checksum() {
        val samplePayload = "Billi signed APK test payload".toByteArray(Charsets.UTF_8)
        val expectedDigest = GitHubReleaseUpdater.computeSha256Hex(samplePayload)
        assertEquals(64, expectedDigest.length)

        val releaseBody = """
            Automated Billi Android release.
            - Version: 1.0.9 (code 9)
            - SHA-256: `$expectedDigest`
        """.trimIndent()

        val extracted = GitHubReleaseUpdater.extractExpectedSha256(releaseBody)
        assertEquals(expectedDigest, extracted)
        assertTrue(GitHubReleaseUpdater.verifySha256(expectedDigest, expectedDigest.uppercase()))
        assertFalse(GitHubReleaseUpdater.verifySha256(expectedDigest, "0".repeat(64)))
        assertFalse(GitHubReleaseUpdater.verifySha256(expectedDigest, "short"))
    }

    @Test
    fun groupBillsByDay_and_computeInsights_produceAccurateStats() {
        val trip = sampleTrip(budgetCents = 500_000L)
        val day1 = 1_700_000_000_000L
        val day2 = day1 + 86_400_000L
        val bills = listOf(
            Bill(id = "b1", title = "Lunch", category = "food", amountCents = 150_000L, paidByMemberId = "m_alice", billDateMillis = day1),
            Bill(id = "b2", title = "Cab", category = "transport", amountCents = 100_000L, paidByMemberId = "m_bob", billDateMillis = day2)
        )

        val groups = DebtSimplifier.groupBillsByDay(bills)
        assertEquals(2, groups.size)
        // Newest day first, with Day 2 on top and Day 1 second
        assertEquals(2, groups[0].dayNumber)
        assertEquals(100_000L, groups[0].totalCents)
        assertEquals(1, groups[1].dayNumber)
        assertEquals(150_000L, groups[1].totalCents)

        val insights = DebtSimplifier.computeInsights(trip, bills)
        assertEquals(250_000L, insights.totalSpendCents)
        assertEquals(0.5f, insights.budgetProgress, 0.001f)
        assertEquals("Alice", insights.topSpenderName)
        assertEquals(2, insights.categoryBreakdown.size)
    }

    @Test
    fun currencyFormatter_formatsInrOnlyWithIndianGrouping() {
        val formatted = CurrencyFormatter.format(12_450_050L) // ₹1,24,500.50
        assertTrue(formatted.startsWith("₹"))
        assertTrue(formatted.endsWith("500.50"))
        assertEquals(125_50L, CurrencyFormatter.parseToMinorUnits("125.50"))
        assertEquals(3333L, CurrencyFormatter.parsePercentToBps("33.33"))
    }

    @Test
    fun multiDayTemplates_and_reactionTypes_areValidAndDeterministic() {
        assertTrue(MultiDayTemplate.presets.size >= 4)
        val homestay = MultiDayTemplate.presets.first { it.id == "stay" }
        assertEquals(BillCategory.STAY, homestay.defaultCategory)
        assertEquals("nights", homestay.unitLabel)

        val ratePerNightCents = CurrencyFormatter.parseToMinorUnits("2400")!!
        val nights = 3
        val totalCents = ratePerNightCents * nights
        assertEquals(720_000L, totalCents)

        assertEquals(ReactionType.VERIFIED, ReactionType.fromWire("verified"))
        assertEquals(ReactionType.CHECK, ReactionType.fromWire("check"))
        assertEquals(ReactionType.THANKS, ReactionType.fromWire("thanks"))
        assertEquals(ReactionType.RECEIPT, ReactionType.fromWire("receipt"))
        assertEquals(ReactionType.VERIFIED, ReactionType.fromWire("unknown_wire"))
    }

    @Test
    fun multiTripNetBalanceAggregation_sumsOwedAndOwingAccurately() {
        val trip1 = sampleTrip()
        val trip2 = sampleTrip().copy(id = "trip_coorg", title = "Coorg")

        // In Trip 1: Alice paid ₹3,000 for 3 people -> Alice is owed +₹2,000
        val bill1 = Bill(
            id = "b_t1",
            tripId = trip1.id,
            title = "Villa Stay",
            amountCents = 300_000L,
            paidByMemberId = "m_alice",
            participantIds = listOf("m_alice", "m_bob", "m_cara"),
            splits = mapOf("m_alice" to 100_000L, "m_bob" to 100_000L, "m_cara" to 100_000L),
            notes = "3 nights with breakfast"
        )
        // In Trip 2: Bob paid ₹1,500 for 3 people -> Alice owes -₹500
        val bill2 = Bill(
            id = "b_t2",
            tripId = trip2.id,
            title = "Jeep Safari",
            amountCents = 150_000L,
            paidByMemberId = "m_bob",
            participantIds = listOf("m_alice", "m_bob", "m_cara"),
            splits = mapOf("m_alice" to 50_000L, "m_bob" to 50_000L, "m_cara" to 50_000L)
        )

        val sum1 = DebtSimplifier.summarizeTrip(trip1, listOf(bill1), "uid_alice", null)
        val sum2 = DebtSimplifier.summarizeTrip(trip2, listOf(bill2), "uid_alice", null)

        val nets = listOfNotNull(sum1.currentUserNetCents, sum2.currentUserNetCents)
        val totalOwed = nets.filter { it > 0L }.sum()
        val totalYouOwe = nets.filter { it < 0L }.sumOf { -it }
        val overallNet = nets.sum()

        assertEquals(200_000L, totalOwed)
        assertEquals(50_000L, totalYouOwe)
        assertEquals(150_000L, overallNet)
        assertEquals("3 nights with breakfast", bill1.notes)
    }

    @Test
    fun billiToastController_infersExpectedSemanticTones() {
        assertEquals(
            com.example.ui.components.BilliToastTone.SUCCESS,
            com.example.ui.components.BilliToastController.inferToastTone("Expense added")
        )
        assertEquals(
            com.example.ui.components.BilliToastTone.SUCCESS,
            com.example.ui.components.BilliToastController.inferToastTone("Settlement recorded")
        )
        assertEquals(
            com.example.ui.components.BilliToastTone.WARNING,
            com.example.ui.components.BilliToastController.inferToastTone("Deleted \"Goa Dinner\"")
        )
        assertEquals(
            com.example.ui.components.BilliToastTone.ERROR,
            com.example.ui.components.BilliToastController.inferToastTone("Failed to save expense")
        )
        assertEquals(
            com.example.ui.components.BilliToastTone.INFO,
            com.example.ui.components.BilliToastController.inferToastTone("Online — trips up to date")
        )
    }
}
