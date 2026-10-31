package com.example.data

import android.content.Context
import com.example.engine.AytosJudgmentEngine
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AytosRepository(
    private val context: Context,
    val preferences: AytosPreferences,
    private val database: AytosDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val judgmentEngine = AytosJudgmentEngine()

    private val _latestJudgment = MutableStateFlow<JudgmentResult?>(null)
    val latestJudgment: StateFlow<JudgmentResult?> = _latestJudgment.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _todaySummary = MutableStateFlow(ShiftSummary())
    val todaySummary: StateFlow<ShiftSummary> = _todaySummary.asStateFlow()

    // Zones flow
    val allZones: Flow<List<ZoneItem>> = database.zoneDao().getAllZones()
        .map { list -> list.map { it.toDomain() } }

    val preferredZones: Flow<List<ZoneItem>> = database.zoneDao().getZonesByType("PREFERRED")
        .map { list -> list.map { it.toDomain() } }

    val avoidedZones: Flow<List<ZoneItem>> = database.zoneDao().getZonesByType("AVOIDED")
        .map { list -> list.map { it.toDomain() } }

    /** آخر نص اتقرا من الشاشة (من خدمة الوصول + الـ OCR) عشان التشخيص. */
    @Volatile
    var lastCapture: String = ""

    /** مكان الكارت العايم على الشاشة، بنخفيه من لقطة الـ OCR عشان ما يقراش نفسه. */
    @Volatile
    var overlayBounds: android.graphics.Rect? = null

    /** Last time a supported ride app was seen by the accessibility service (for auto-stop). */
    @Volatile
    var lastActivityTime: Long = System.currentTimeMillis()

    private var lastOrderSignature: String = ""
    private var lastOrderTime: Long = 0L
    private var suppressedSignature: String = ""
    private var lastOrderKey: String = ""
    private var suppressedKey: String = ""
    private var lastRecordedKey: String = ""
    private var lastRecordedTime: Long = 0L

    // On opportunity alert callback
    var onOpportunityChime: ((JudgmentResult) -> Unit)? = null

    init {
        // Pre-populate some popular Egyptian zones if empty
        scope.launch {
            refreshTodaySummary()
            checkDefaultZones()
        }
    }

    suspend fun refreshTodaySummary() {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val countTotal = database.tripHistoryDao().countTripsSince(startOfDay)
        val countAccepted = database.tripHistoryDao().countAcceptedTripsSince(startOfDay)
        val sumProfit = database.tripHistoryDao().sumNetProfitSince(startOfDay) ?: 0.0

        _todaySummary.value = ShiftSummary(
            totalOrdersScanned = countTotal,
            acceptedOrdersCount = countAccepted,
            totalEstimatedNetProfit = sumProfit,
            avoidedCostSaved = database.tripHistoryDao().sumAvoidedCostSince(startOfDay) ?: 0.0
        )
    }

    private suspend fun checkDefaultZones() {
        database.zoneDao().getAllZones().firstOrNull()?.let { list ->
            if (list.isEmpty()) {
                val defaults = listOf(
                    ZoneEntity(name = "المهندسين والدقي", governorate = "الجيزة", type = "PREFERRED", notes = "طلب مستمر وطلبات راقية"),
                    ZoneEntity(name = "التجمع الخامس", governorate = "القاهرة", type = "PREFERRED", notes = "طرق سريعة وأجرة كويسة"),
                    ZoneEntity(name = "وسط البلد والزمالك", governorate = "القاهرة", type = "PREFERRED", notes = "مشاوير سريعة"),
                    ZoneEntity(name = "طريق السويس وقت الذروة", governorate = "القاهرة", type = "AVOIDED", notes = "زحمة وتوقفات طويلة"),
                    ZoneEntity(name = "المناطق الصناعية المتطرفة", governorate = "القاهرة", type = "AVOIDED", notes = "طرق غير ممهدة ومفيش رجوع")
                )
                defaults.forEach { database.zoneDao().insertZone(it) }
            }
        }
    }

    /**
     * Ingest an order parsed from Uber, DiDi, or inDrive.
     * Runs the personalized judgment engine and updates the overlay.
     */
    fun processOrder(order: OrderDetails, record: Boolean = true) {
        val currentRules = preferences.rulesFlow.value

        // Ignore the same card re-read repeatedly (accessibility fires many events per card)
        // التوقيع بيعتمد على الأرقام بس (مش العناوين) عشان نص الـ OCR بيتغير من قراءة للتانية
        val signature = "${order.app}|${order.fare}|${order.pickupDistanceKm}|${order.tripDistanceKm}"
        val now = System.currentTimeMillis()
        // مفتاح التجاهل: التطبيق + السعر بس، عشان تحديث مسافة جاية متأخر ما يرجّعش كارت السواق قفله
        val orderKey = "${order.app}|${order.fare}"
        if (record) {
            if (orderKey == suppressedKey) return // السواق قفل الكارت ده بإيده
            if (signature == lastOrderSignature && (_isOverlayVisible.value || now - lastOrderTime < 20_000L)) return
            lastOrderSignature = signature
            lastOrderKey = orderKey
            lastOrderTime = now
        }

        scope.launch {
            val avoidedList = database.zoneDao().getZonesByType("AVOIDED").firstOrNull()?.map { it.name } ?: emptyList()
            val preferredList = database.zoneDao().getZonesByType("PREFERRED").firstOrNull()?.map { it.name } ?: emptyList()

            val judgment = judgmentEngine.evaluate(
                order = order,
                rules = currentRules,
                avoidedZoneNames = avoidedList,
                preferredZoneNames = preferredList
            )

            _latestJudgment.value = judgment
            _isOverlayVisible.value = true

            // Trigger Opportunity chime if it meets all high profitability conditions
            if (judgment.isOpportunity) {
                onOpportunityChime?.invoke(judgment)
            }

            // Record to history if valid fare exists
            val recordKey = "${order.app}|${order.fare}"
            val isNewTrip = recordKey != lastRecordedKey || System.currentTimeMillis() - lastRecordedTime > 45_000L
            if (record && order.fare != null && judgment.verdict != VerdictType.WAIT_DETAILS && isNewTrip) {
                lastRecordedKey = recordKey
                lastRecordedTime = System.currentTimeMillis()
                database.tripHistoryDao().insertTrip(
                    TripHistoryEntity(
                        app = order.app.titleArabic,
                        fare = order.fare,
                        netProfit = judgment.netProfit ?: 0.0,
                        verdict = judgment.verdict.name,
                        pickupKm = order.pickupDistanceKm ?: 0.0,
                        tripKm = order.tripDistanceKm ?: 0.0,
                        reasonsSummary = judgment.reasons.joinToString(" • ")
                    )
                )
                refreshTodaySummary()
            }
        }
    }

    /**
     * @param byUser لو السواق هو اللي قفل الكارت، بنفضل مانظهروش تاني لنفس الطلب.
     * لو اتقفل لوحده (الطلب اختفى من الشاشة) بنصفّر عشان الطلب الجاي يظهر عادي.
     */
    fun dismissOverlay(byUser: Boolean = false) {
        if (byUser) {
            suppressedSignature = lastOrderSignature
            suppressedKey = lastOrderKey
        } else {
            lastOrderSignature = ""
            suppressedSignature = ""
            lastOrderKey = ""
            suppressedKey = ""
        }
        _isOverlayVisible.value = false
        overlayBounds = null
    }

    suspend fun addZone(name: String, governorate: String, type: ZoneType, notes: String) {
        database.zoneDao().insertZone(
            ZoneEntity(name = name, governorate = governorate, type = type.name, notes = notes)
        )
    }

    suspend fun deleteZone(zone: ZoneItem) {
        database.zoneDao().deleteZone(ZoneEntity.fromDomain(zone))
    }

    companion object {
        @Volatile
        private var INSTANCE: AytosRepository? = null

        fun getInstance(context: Context): AytosRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AytosDatabase.getInstance(context)
                val prefs = AytosPreferences(context)
                val repo = AytosRepository(context, prefs, db)
                INSTANCE = repo
                repo
            }
        }
    }
}
