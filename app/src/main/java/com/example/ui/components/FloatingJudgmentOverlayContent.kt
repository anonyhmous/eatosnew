package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.JudgmentResult
import com.example.model.VerdictType
import com.example.ui.theme.*

@Composable
fun FloatingJudgmentOverlayContent(
    judgment: JudgmentResult,
    isExpanded: Boolean,
    opacity: Float = 0.95f,
    scale: Float = 1.0f,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val verdictColor = judgment.verdict.primaryColor()
    val verdictBg = judgment.verdict.bgColor()

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(12.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(AppCard.copy(alpha = opacity))
            .border(1.5.dp, verdictColor.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .animateContentSize()
            .testTag("floating_overlay_root")
    ) {
        if (!isExpanded) {
            // Compact Pill Mode
            Row(
                modifier = Modifier
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Verdict indicator dot & text
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(verdictColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = judgment.verdict.labelArabic,
                        color = AppOnVerdict,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                judgment.grossFare?.let { fare ->
                    Text(
                        text = "${fare.toInt()} ج.م",
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = InterFont
                    )
                }

                judgment.netProfit?.let { net ->
                    Text(
                        text = "• صافي ${net.toInt()}+",
                        color = VerdictAccept,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        fontFamily = InterFont
                    )
                }

                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "تكبير",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            // Full Expanded Breakdown Card
            Column(
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with App name, Verdict badge, and actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when (judgment.order.app.name) {
                                "UBER" -> UberBlack
                                "DIDI" -> DidiOrange
                                else -> IndriveGreen
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = judgment.order.app.titleArabic.take(1),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = judgment.order.app.titleArabic,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = AppTextPrimary
                        )

                        // Verdict chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(verdictBg)
                                .border(1.dp, verdictColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = judgment.verdict.labelArabic,
                                color = verdictColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Window control buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "تصغير",
                                tint = AppTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = AppTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Net Profit & Fare Hero Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (judgment.netProfit != null && judgment.netProfit > 0) VerdictAcceptBg else AppSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "صافي بجيبك بعد كل المصاريف:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppTextSecondary
                            )
                            judgment.netProfit?.let { net ->
                                Text(
                                    text = "${String.format("%.1f", net)} ج.م",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (net > 0) VerdictAccept else VerdictReject,
                                    fontFamily = InterFont
                                )
                            }
                        }

                        // Expenses breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الأجرة: ${judgment.grossFare?.toInt() ?: "--"} ج.م",
                                fontSize = 11.sp,
                                color = AppTextSecondary
                            )
                            Text(
                                text = "عمولة: -${judgment.commissionDeduction.toInt()} ج.م",
                                fontSize = 11.sp,
                                color = AppTextSecondary
                            )
                            Text(
                                text = "بنزين: -${judgment.fuelCostEstimate.toInt()} ج.م",
                                fontSize = 11.sp,
                                color = AppTextSecondary
                            )
                            Text(
                                text = "صيانة: -${judgment.maintenanceEstimate.toInt()} ج.م",
                                fontSize = 11.sp,
                                color = AppTextSecondary
                            )
                        }
                    }
                }

                // Counter-offer prompt if orange/negotiable
                judgment.counterOfferSuggestedFare?.let { counterOffer ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VerdictConsiderBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VerdictConsider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "اقتراح التفاوض العادل لك:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = VerdictConsider
                            )
                            Text(
                                text = "فاوض بـ ${counterOffer.toInt()} ج.م",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = VerdictConsider,
                                fontFamily = InterFont
                            )
                        }
                    }
                }

                // Going Home / Going Destination advancement badge
                judgment.directionAdvancementPct?.let { pct ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppPrimaryTint,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (pct >= 0) Icons.Default.NearMe else Icons.Default.Navigation,
                                contentDescription = null,
                                tint = AppPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (pct >= 0) "بيقرّبك $pct% باتجاه وجهتك" else "بيبعدك ${-pct}% عن وجهتك",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppPrimary
                            )
                        }
                    }
                }

                // Metrics row: Pickup, Trip, Rate/km
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricMiniChip(
                        label = "الاستلام",
                        value = "${judgment.order.pickupDistanceKm?.let { String.format("%.1f", it) } ?: "--"} كم"
                    )
                    MetricMiniChip(
                        label = "الرحلة",
                        value = "${judgment.order.tripDistanceKm?.let { String.format("%.1f", it) } ?: "--"} كم"
                    )
                    MetricMiniChip(
                        label = "سعر الكيلو",
                        value = "${judgment.ratePerKm?.let { String.format("%.1f", it) } ?: "--"} ج/كم"
                    )
                    MetricMiniChip(
                        label = "الراكب",
                        value = "${judgment.order.passengerRating?.let { String.format("%.2f", it) } ?: "--"} ★"
                    )
                }

                // Reasons or Unparsed Fields
                if (judgment.reasons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        judgment.reasons.take(3).forEach { reason ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(verdictColor, CircleShape)
                                )
                                Text(
                                    text = reason,
                                    fontSize = 11.sp,
                                    color = AppTextPrimary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                if (judgment.unparsedFields.isNotEmpty()) {
                    Text(
                        text = "تنبيه: " + judgment.unparsedFields.joinToString("، "),
                        fontSize = 11.sp,
                        color = AppTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricMiniChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = AppTextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppTextPrimary, fontFamily = InterFont)
    }
}
