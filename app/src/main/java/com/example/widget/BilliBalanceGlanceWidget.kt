package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.WidgetSnapshot

val WidgetQuickAddTripIdKey = ActionParameters.Key<String>("extra_quick_add_trip_id")
val WidgetOpenTripIdKey = ActionParameters.Key<String>("extra_open_trip_id")

/**
 * Material 3 Home Screen Quick-Add Widget (Jetpack Glance).
 * Displays the user's current net trip balance (e.g. "You are owed ₹1,420")
 * and a + button that opens the Add Expense editor directly.
 */
class BilliBalanceGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = try {
            AppPreferencesRepository(context).currentPreferences().widgetSnapshot
        } catch (_: Exception) {
            WidgetSnapshot()
        }

        provideContent {
            GlanceTheme {
                BilliWidgetContent(snapshot = snapshot)
            }
        }
    }

    @Composable
    private fun BilliWidgetContent(snapshot: WidgetSnapshot) {
        val openAction = if (snapshot.tripId.isNotBlank()) {
            actionStartActivity<MainActivity>(
                actionParametersOf(WidgetOpenTripIdKey to snapshot.tripId)
            )
        } else {
            actionStartActivity<MainActivity>()
        }

        val quickAddAction = if (snapshot.tripId.isNotBlank()) {
            actionStartActivity<MainActivity>(
                actionParametersOf(WidgetQuickAddTripIdKey to snapshot.tripId)
            )
        } else {
            actionStartActivity<MainActivity>()
        }

        val amountColor = when {
            snapshot.netBalanceCents > 0L -> GlanceTheme.colors.primary
            snapshot.netBalanceCents < 0L -> GlanceTheme.colors.error
            else -> GlanceTheme.colors.onSurface
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(22.dp)
                .background(GlanceTheme.colors.surface)
                .clickable(openAction)
                .padding(14.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "BILLI • ${snapshot.tripTitle.take(20)}",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = snapshot.balanceSubtitle,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = snapshot.balanceAmountText,
                            style = TextStyle(
                                color = amountColor,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(GlanceModifier.width(10.dp))

                    // Material 3 Quick-Add (+) Button
                    Box(
                        modifier = GlanceModifier
                            .size(48.dp)
                            .cornerRadius(16.dp)
                            .background(GlanceTheme.colors.primary)
                            .clickable(quickAddAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                if (snapshot.pendingSyncCount > 0) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = "${snapshot.pendingSyncCount} offline updates queued",
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class BilliGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BilliBalanceGlanceWidget()
}

suspend fun refreshBilliWidgets(context: Context) {
    try {
        BilliBalanceGlanceWidget().updateAll(context.applicationContext)
    } catch (_: Exception) {
    }
}
