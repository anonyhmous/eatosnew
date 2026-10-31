package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.IosCard
import com.example.ui.components.IosPrimaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AppPrimary

/**
 * شاشة "المزيد": المظهر (نهاري/ليلي)، إعداد الصلاحيات، تشخيص القراءة، والتزامات التطبيق.
 * التطبيق خاص بصاحبه، فمفيش اشتراكات ولا تراخيص.
 */
@Composable
fun MoreScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    captureProvider: () -> String,
    onOpenSetupWizard: () -> Unit
) {
    val context = LocalContext.current
    var showCapture by remember { mutableStateOf(false) }
    var captureText by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "المزيد",
            subtitle = "المظهر والصلاحيات وتشخيص القراءة"
        )

        // المظهر
        IosCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "الوضع الليلي",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDark) "شغّال دلوقتي" else "مقفول (الوضع النهاري)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Switch(checked = isDark, onCheckedChange = { onToggleTheme() })
            }
        }

        // الصلاحيات
        IosCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "إعداد الصلاحيات",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "خدمة الوصول (قراءة الشاشة واللقطة للـ OCR)، والظهور فوق التطبيقات، والإشعارات، واستثناء البطارية.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            IosPrimaryButton(
                text = "افتح معالج الإعداد",
                onClick = onOpenSetupWizard,
                icon = Icons.Default.Settings,
                testTag = "open_setup_wizard_button"
            )
        }

        // تشخيص القراءة
        IosCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "تشخيص القراءة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "بيعرض آخر نص اتقرا من الشاشة (نص + OCR). افتح إن درايف أو ديدي لحد ما يظهر طلب، ارجع هنا واضغط عرض. النص ده في ذاكرة التطبيق بس ومبيتحفظش، وممكن يحتوي أسماء، فراجعه قبل ما تبعته لأي حد.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    captureText = captureProvider()
                    showCapture = true
                    copied = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("اعرض آخر قراءة")
            }

            if (showCapture) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = captureText.ifBlank { "لسه ما اتقرا حاجة. افتح تطبيق التوصيل وجرّب تاني." },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (captureText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("aytos-capture", captureText))
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (copied) "اتنسخ" else "انسخ النص")
                    }
                }
            }
        }

        // التزامات التطبيق
        IosCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "التزامات كابتن أيتوس",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val points = listOf(
                "أداة قراءة وحساب فقط.",
                "مبيضغطش على أي زرار، ومبيقبلش ولا بيرفض ولا بيفاوض بدالك.",
                "بيقرا نص الشاشة ولقطة شاشة (OCR) بس لما يكون إن درايف أو ديدي أو أوبر هو اللي قدامك.",
                "اللقطة بتتقرا في الذاكرة وبتتمسح فوراً، ومبتتحفظش.",
                "مبيحفظش اسم الراكب ولا رقمه.",
                "كل الحسابات على موبايلك."
            )
            points.forEachIndexed { index, text ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
                Text(
                    text = "${index + 1}. $text",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = "كابتن أيتوس • نسخة خاصة",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )
    }
}
