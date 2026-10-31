package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.example.data.AytosRepository
import com.example.engine.AytosCardParser
import com.example.engine.AytosOcrEngine
import com.example.engine.BubbleColor
import com.example.engine.OcrOutput
import com.example.model.SupportedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * خدمة قراءة الطلبات (كابتن أيتوس):
 * - بتقرا بس إن درايف وديدي وأوبر.
 * - بتقرا من مصدرين مع بعض: نص الشاشة (Accessibility) + لقطة شاشة بتتقرا بالـ OCR.
 *   الـ OCR أساسي ودايم، لأن أرقام كتير (زي مسافة الرحلة على الخريطة) مبتظهرش كنص.
 * - للقراءة والحكم بس: ما بتضغطش ولا بتقبل ولا بترفض ولا بتفاوض نيابة عن السواق.
 * - اللقطة بتتقرا في الذاكرة وبتتمسح فوراً، ومبتتحفظش.
 * - مفيش حكم بيظهر غير لو الشاشة فعلاً كارت طلب (سعر + مسافة).
 */
class AytosAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cardParser = AytosCardParser()
    private val ocrEngine: AytosOcrEngine by lazy { AytosOcrEngine() }
    private var repository: AytosRepository? = null

    private var lastProcessTime = 0L
    private var lastCardSeenTime = 0L
    private var lastSupportedSeenTime = 0L
    private var hideJob: Job? = null
    private var tickJob: Job? = null

    // آخر مسافات اتقرت للطلب الحالي، عشان قراءة ناقصة ما تمسحش قراءة صح اتقرت قبلها
    private var memPickup: Double? = null
    private var memTrip: Double? = null

    private var ocrBusy = false
    private var lastOcrAt = 0L

    private fun repo(): AytosRepository {
        return repository ?: AytosRepository.getInstance(applicationContext).also { repository = it }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        try {
            repo()
            // الخدمة اتربطت بالنظام، ودي أنسب لحظة نشغّل فيها الخدمة الدايمة
            AytosForegroundService.start(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "onServiceConnected failed", e)
        }

        // قراءة دورية مستمرة: الشاشة الثابتة مبتبعتش أحداث، فمن غير ده الكارت كان بيتقفل ومبيرجعش
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            while (isActive) {
                val idle = System.currentTimeMillis() - lastSupportedSeenTime > IDLE_AFTER_MS
                delay(if (idle) TICK_IDLE_MS else TICK_MS)
                try {
                    scan()
                } catch (e: Exception) {
                    Log.e(TAG, "periodic scan failed", e)
                }
            }
        }

        // نقفل الكارت بس لما الطلب يختفي فعلاً أو السواق يسيب التطبيق
        hideJob?.cancel()
        hideJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val r = repo()
                val now = System.currentTimeMillis()
                val appGone = lastSupportedSeenTime > 0L && now - lastSupportedSeenTime > APP_GONE_MS
                val cardGone = lastCardSeenTime > 0L && now - lastCardSeenTime > CARD_GONE_MS
                if (appGone || cardGone) {
                    if (r.isOverlayVisible.value || lastCardSeenTime > 0L) r.dismissOverlay()
                    lastCardSeenTime = 0L
                    resetMemory()
                }
            }
        }
    }

    private fun resetMemory() {
        memPickup = null
        memTrip = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            handleEvent(event)
        } catch (e: Exception) {
            // أي خطأ بيخرج من هنا بيقفل التطبيق كله، عشان كده بنمسكه دايماً
            Log.e(TAG, "Error while reading screen", e)
        }
    }

    private fun handleEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (SupportedApp.fromPackage(pkg) == null) return // أي تطبيق تاني بيتتجاهل تماماً
        scan()
    }

    /** بتقرا الشاشة (من الأحداث ومن المؤقت الدوري)، ولو فيها كارت طلب بتبعته للحكم. */
    private fun scan() {
        val r = repo()
        if (!r.preferences.isReaderActiveFlow.value) return

        val now = System.currentTimeMillis()
        if (now - lastProcessTime < THROTTLE_MS) return
        lastProcessTime = now

        // 1) نص الشاشة من كل نوافذ التطبيقات المدعومة (الكارت غالباً نافذة منفصلة عن الخريطة)
        var app: SupportedApp? = null
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                val root = w.root ?: continue
                val wApp = SupportedApp.fromPackage(root.packageName?.toString()) ?: continue
                app = wApp
                roots.add(root)
            }
        } catch (e: Exception) {
            Log.w(TAG, "windows scan failed, falling back to active window", e)
        }
        if (roots.isEmpty()) {
            val root = rootInActiveWindow
            val rootApp = SupportedApp.fromPackage(root?.packageName?.toString())
            if (root != null && rootApp != null) {
                app = rootApp
                roots.add(root)
            }
        }
        val currentApp = app ?: return // مفيش تطبيق توصيل قدام السواق

        lastSupportedSeenTime = now
        r.lastActivityTime = now

        // الأول بنقرا العناصر الظاهرة بس (عشان شاشات قديمة ورا الكارت ما تلخبطش)، ولو طلع فاضي نقرا الكل
        val builder = StringBuilder()
        for (root in roots) extractNodeTexts(root, builder, 0, true)
        if (builder.isBlank()) {
            for (root in roots) extractNodeTexts(root, builder, 0, false)
        }
        val screenText = builder.toString().trim()
        r.lastCapture = "[نص الشاشة]\n$screenText"

        // 2) لو النص لوحده كفاية بنعرض الحكم فوراً، والـ OCR بيكمّل الناقص بعدها
        if (screenText.isNotBlank()) evaluate(currentApp, screenText, null)

        // 3) الـ OCR شغال دايماً (بفاصل زمني) حتى لو النص فاضي
        requestOcr(currentApp, screenText)
    }

    /**
     * بيحوّل النص + نتيجة الـ OCR لطلب، ولو شكله كارت طلب بيبعته للحكم.
     * المسافات: الاستلام من نص الشاشة (أو الفقاعة الزرقا)، والرحلة من الفقاعة الخضرا.
     * وبنحتفظ بآخر قراءة صح للطلب الحالي عشان قراءة ناقصة ما تمسحهاش.
     */
    private fun evaluate(app: SupportedApp, text: String, ocr: OcrOutput?) {
        val r = repo()

        // شاشة الرحلات تحت بعضها مش كارت رحلة واحدة: مفيش حكم، والكارت القديم يتقفل
        if (app == SupportedApp.INDRIVER && cardParser.looksLikeInDriveList(text)) {
            if (r.isOverlayVisible.value || lastCardSeenTime > 0L) r.dismissOverlay()
            lastCardSeenTime = 0L
            resetMemory()
            return
        }

        val parsed = cardParser.parse(text, app)
        var pickup = parsed.pickupDistanceKm
        var trip = parsed.tripDistanceKm

        var order = parsed
        if (ocr != null) {
            val blue = ocr.bubbles.firstOrNull { it.color == BubbleColor.BLUE }?.km
            val green = ocr.bubbles.firstOrNull { it.color == BubbleColor.GREEN }?.km
            if (green != null) trip = green          // اللون هو المرجع للرحلة
            if (pickup == null) pickup = blue

            // تكملة السعر (ولو مش إن درايف، تخمين المسافات القديم لو مفيش ألوان ولا نص)
            val noColors = blue == null && green == null
            val guess = app != SupportedApp.INDRIVER && noColors && pickup == null && trip == null
            order = cardParser.fillFromOcrNumbers(parsed, ocr.numbers, guess)
            if (guess) {
                pickup = order.pickupDistanceKm
                trip = order.tripDistanceKm
            }
        }

        // طلب جديد (استلام اختلف كتير) = ننسى مسافة الرحلة القديمة
        val oldPickup = memPickup
        if (pickup != null && oldPickup != null && kotlin.math.abs(pickup - oldPickup) > NEW_ORDER_PICKUP_DELTA) {
            memTrip = null
        }
        if (pickup != null) memPickup = pickup
        if (trip != null) memTrip = trip

        order = order.copy(pickupDistanceKm = pickup ?: memPickup, tripDistanceKm = trip ?: memTrip)

        // مش كارت طلب (شاشة رئيسية، أرباح، قوائم...) = مفيش حكم وهمي
        if (!cardParser.looksLikeOrderCard(order)) return

        lastCardSeenTime = System.currentTimeMillis()
        r.processOrder(order)
    }

    // ------------------------------------------------------------------ OCR

    private fun requestOcr(app: SupportedApp, screenText: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return // لقطة الشاشة من الخدمة محتاجة أندرويد 11+

        val now = SystemClock.elapsedRealtime()
        if (ocrBusy && now - lastOcrAt > OCR_STUCK_MS) ocrBusy = false // لو الرد اتأخر قوي نكمّل
        val recentlySawCard = System.currentTimeMillis() - lastCardSeenTime < 10_000L
        val interval = if (recentlySawCard) OCR_FAST_MS else OCR_IDLE_MS
        if (ocrBusy || now - lastOcrAt < interval) return

        ocrBusy = true
        lastOcrAt = now
        try {
            captureScreen(app, screenText)
        } catch (e: Exception) {
            ocrBusy = false
            Log.w(TAG, "screenshot request failed", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreen(app: SupportedApp, screenText: String) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    try {
                        // أمان: ما نقراش غير لو تطبيق التوصيل هو اللي قدام السواق فعلاً
                        val activePkg = rootInActiveWindow?.packageName?.toString()
                        if (SupportedApp.fromPackage(activePkg) == null) {
                            screenshot.hardwareBuffer.close()
                            ocrBusy = false
                            return
                        }
                        val bitmap = toBitmap(screenshot)
                        if (bitmap == null) {
                            ocrBusy = false
                            return
                        }
                        val prepared = prepareForOcr(bitmap)
                        ocrEngine.recognize(prepared) { output ->
                            ocrBusy = false
                            prepared.recycle()
                            if (output != null) {
                                val r = repo()
                                val bubblesInfo = output.bubbles.joinToString(" | ") {
                                    "${if (it.color == BubbleColor.BLUE) "أزرق" else "أخضر"}=${it.km}"
                                }
                                r.lastCapture = "[نص الشاشة]\n$screenText\n\n[OCR]\n${output.text}\n\n[فقاعات ملوّنة] $bubblesInfo"
                                evaluate(app, screenText + "\n" + output.text, output)
                            }
                        }
                    } catch (e: Exception) {
                        ocrBusy = false
                        Log.w(TAG, "OCR pipeline failed", e)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    ocrBusy = false
                    Log.w(TAG, "takeScreenshot failed: $errorCode")
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun toBitmap(result: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = result.hardwareBuffer
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()
            return software
        } finally {
            buffer.close()
        }
    }

    /**
     * بنصغّر اللقطة (أسرع)، وبنغطي شريط الحالة وكارت أيتوس العايم عشان الـ OCR ما يقراش نفسه.
     * اللقطة الأصلية بتتمسح بعد النسخ.
     */
    private fun prepareForOcr(source: Bitmap): Bitmap {
        val scale = if (source.width > MAX_OCR_WIDTH) MAX_OCR_WIDTH.toFloat() / source.width else 1f
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()

        val black = Paint().apply { color = Color.BLACK }
        // شريط الحالة (الساعة والبطارية) فيه أرقام مش مطلوبة
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.045f, black)
        // كارت أيتوس العايم
        repo().overlayBounds?.let { b ->
            canvas.drawRect(
                b.left * scale, b.top * scale,
                b.right * scale, b.bottom * scale,
                black
            )
        }
        return out
    }

    // ------------------------------------------------------------ node text

    private fun extractNodeTexts(node: AccessibilityNodeInfo?, builder: StringBuilder, depth: Int, visibleOnly: Boolean) {
        if (node == null || depth > MAX_DEPTH || builder.length > MAX_CHARS) return
        if (visibleOnly && !node.isVisibleToUser) return

        node.text?.let {
            if (it.isNotBlank()) builder.append(it).append("\n")
        }
        node.contentDescription?.let {
            if (it.isNotBlank() && it.toString() != node.text?.toString()) builder.append(it).append("\n")
        }

        for (i in 0 until node.childCount) {
            extractNodeTexts(node.getChild(i), builder, depth + 1, visibleOnly)
        }
    }

    override fun onInterrupt() {
        // الخدمة اتقاطعت، مفيش حاجة نعملها
    }

    override fun onDestroy() {
        hideJob?.cancel()
        tickJob?.cancel()
        serviceScope.cancel()
        ocrEngine.close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AytosAccessibility"
        private const val THROTTLE_MS = 500L
        private const val CARD_GONE_MS = 6_000L
        private const val APP_GONE_MS = 3_000L
        private const val TICK_MS = 1_000L
        private const val TICK_IDLE_MS = 3_000L
        private const val IDLE_AFTER_MS = 10_000L
        private const val NEW_ORDER_PICKUP_DELTA = 0.6
        private const val OCR_FAST_MS = 1_200L
        private const val OCR_IDLE_MS = 2_500L
        private const val OCR_STUCK_MS = 6_000L
        private const val MAX_OCR_WIDTH = 1080
        private const val MAX_DEPTH = 40
        private const val MAX_CHARS = 8_000
    }
}
