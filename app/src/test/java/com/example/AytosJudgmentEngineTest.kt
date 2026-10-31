package com.example

import com.example.engine.AytosCardParser
import com.example.engine.AytosJudgmentEngine
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

class AytosJudgmentEngineTest {

    private val engine = AytosJudgmentEngine()
    private val parser = AytosCardParser()

    @Test
    fun testAcceptVerdictOnHighProfitTrip() {
        val rules = DriverRules(
            minFare = 35.0,
            minRatePerKm = 8.0,
            pickupThresholdKm = 1.5,
            fuelPricePerLiter = 15.0,
            engineSize = EngineSize.CC_1400_TO_1600, // 7.2 L/100km
            maintenanceCostPerKm = 1.0,
            uberCommissionPct = 25.0
        )

        val order = OrderDetails(
            app = SupportedApp.UBER,
            fare = 150.0,
            pickupDistanceKm = 1.0, // below 1.5 threshold -> not penalized
            tripDistanceKm = 10.0,
            passengerRating = 4.95,
            passengerTripsCount = 45,
            orderAgeSeconds = 12
        )

        val result = engine.evaluate(order, rules)

        assertEquals(VerdictType.ACCEPT, result.verdict)
        assertTrue(result.isOpportunity)
        assertNotNull(result.netProfit)
        assertTrue(result.netProfit!! > 80.0) // 150 - 37.5 (comm) - ~11 (fuel) - ~11 (maint) = ~90 EGP net
    }

    @Test
    fun testRejectOnLowFareAndFarPickup() {
        val rules = DriverRules(
            minFare = 40.0,
            maxPickupDistanceKm = 3.0
        )

        val order = OrderDetails(
            app = SupportedApp.UBER,
            fare = 25.0, // below minFare 40
            pickupDistanceKm = 4.5, // exceeds max pickup 3.0
            tripDistanceKm = 3.0
        )

        val result = engine.evaluate(order, rules)

        assertEquals(VerdictType.REJECT, result.verdict)
        assertFalse(result.isOpportunity)
        assertTrue(result.reasons.any { it.contains("أقل من حدك الأدنى") })
        assertTrue(result.reasons.any { it.contains("الاستلام بعيد") })
    }

    @Test
    fun testRejectOnForbiddenKeywordInNote() {
        val rules = DriverRules(
            forbiddenKeywords = listOf("سجاير", "دخان")
        )

        val order = OrderDetails(
            app = SupportedApp.DIDI,
            fare = 100.0,
            pickupDistanceKm = 1.0,
            tripDistanceKm = 8.0,
            passengerNote = "معلش هشرب سجاير في الطريق"
        )

        val result = engine.evaluate(order, rules)

        assertEquals(VerdictType.REJECT, result.verdict)
        assertTrue(result.reasons.any { it.contains("سجاير") })
    }

    @Test
    fun testWaitDetailsWhenNumbersAreMissing() {
        val rules = DriverRules()
        val order = OrderDetails(
            app = SupportedApp.INDRIVER,
            fare = null,
            pickupDistanceKm = null,
            tripDistanceKm = null
        )

        val result = engine.evaluate(order, rules)

        assertEquals(VerdictType.WAIT_DETAILS, result.verdict)
        assertTrue(result.unparsedFields.isNotEmpty())
    }

    @Test
    fun testCardParserArabicNumbersAndDistances() {
        val rawText = """
            طلب أوبر إكس
            95.50 ج.م
            1.8 كم للاستلام
            12.4 كم مدة الرحلة 25 دقيقة
            ★ 4.88 (42 رحلة)
            طريقة الدفع: نقدي كاش
            منذ 15 ثانية
            الدقي إلى التجمع
        """.trimIndent()

        val parsed = parser.parse(rawText, SupportedApp.UBER)

        assertEquals(95.50, parsed.fare ?: 0.0, 0.01)
        assertEquals(1.8, parsed.pickupDistanceKm ?: 0.0, 0.01)
        assertEquals(12.4, parsed.tripDistanceKm ?: 0.0, 0.01)
        assertEquals(4.88, parsed.passengerRating ?: 0.0, 0.01)
        assertEquals(PaymentMethod.CASH, parsed.paymentMethod)
        assertEquals(15, parsed.orderAgeSeconds)
    }
}
