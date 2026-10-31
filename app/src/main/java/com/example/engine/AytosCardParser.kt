package com.example.engine

import com.example.model.OrderDetails
import com.example.model.PaymentMethod
import com.example.model.SupportedApp
import java.util.regex.Pattern

/**
 * Robust multilingual (Arabic & English) parser for extracting ride offer card metrics.
 * Designed to accurately extract numbers without guessing.
 */
class AytosCardParser {

    private val fareRegex = Pattern.compile(
        """(?:ج\.م|جنيه|جنيهاً|E£|£|LE|EGP|SAR|AED|USD|ر\.س|\$)?\s*([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:ج\.م|جنيه|جنيهاً|E£|£|LE|EGP|SAR|AED|USD|ر\.س|\$)?""",
        Pattern.CASE_INSENSITIVE
    )

    private val distanceKmRegex = Pattern.compile(
        """([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:كم|كيلومتر|km)""",
        Pattern.CASE_INSENSITIVE
    )

    private val distanceMeterRegex = Pattern.compile(
        """([0-9]+)\s*(?:متر|م\b|m\b)""",
        Pattern.CASE_INSENSITIVE
    )

    private val ratingRegex = Pattern.compile(
        """(?:★|⭐)?\s*([45]\.[0-9]{1,2})\s*(?:★|⭐)?"""
    )

    private val tripsRegex = Pattern.compile(
        """([0-9]{1,5})\s*(?:رحلة|مشوار|trips?|rides?)""",
        Pattern.CASE_INSENSITIVE
    )

    private val orderAgeRegex = Pattern.compile(
        """([0-9]{1,3})\s*(?:ث|ثانية|ثوان|sec|s\b)""",
        Pattern.CASE_INSENSITIVE
    )

    /** Converts Arabic-Indic / Persian digits and Arabic separators to plain ASCII so regexes work on Arabic phones. */
    private fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                '\u066B' -> sb.append('.')
                '\u066C' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * A real ride-offer card shows a price AND at least one distance.
     * Anything else (home screen, earnings, menus) must not produce a judgment.
     */
    fun looksLikeOrderCard(order: OrderDetails): Boolean {
        return order.fare != null && (order.pickupDistanceKm != null || order.tripDistanceKm != null)
    }

    /**
     * بيكمّل الناقص (السعر / المسافات) من أرقام الـ OCR، بالاعتماد على حجم الرقم ومكانه
     * مش على الكلام العربي. الأولوية دايماً لللي اتقرا من نص الشاشة نفسه.
     * ده تخمين منظم: السعر هو أكبر رقم على الشاشة، والمسافات هي الأرقام العشرية بالترتيب من فوق لتحت.
     */
    fun fillFromOcrNumbers(order: OrderDetails, numbers: List<OcrNumber>): OrderDetails {
        var fare = order.fare
        var pickup = order.pickupDistanceKm
        var trip = order.tripDistanceKm

        if (fare == null) {
            fare = numbers
                .filter { it.value in 20.0..2500.0 }
                .maxWithOrNull(compareBy<OcrNumber>({ it.heightPx }, { -it.topPx }))
                ?.value
        }

        if (pickup == null && trip == null) {
            val distances = numbers
                .filter { n ->
                    n.text.contains('.') &&
                        n.value in 0.1..80.0 &&
                        n.value != fare &&
                        // رقمين بعد العلامة وبين 4 و5 غالباً تقييم راكب (زي 4.92) مش مسافة
                        !(n.text.substringAfter('.').length == 2 && n.value in 4.0..5.0)
                }
                .sortedBy { it.topPx }

            when {
                distances.size >= 2 -> {
                    pickup = distances[0].value
                    trip = distances[1].value
                }
                distances.size == 1 -> {
                    val d = distances[0].value
                    if (d < 4.0) pickup = d else trip = d
                }
            }
        }

        return order.copy(fare = fare, pickupDistanceKm = pickup, tripDistanceKm = trip)
    }

    fun parse(originalText: String, app: SupportedApp, isListCard: Boolean = false): OrderDetails {
        val rawText = normalizeDigits(originalText)
        var fare: Double? = null
        var pickupKm: Double? = null
        var tripKm: Double? = null
        var rating: Double? = null
        var tripsCount: Int? = null
        var orderAgeSec: Int? = null
        var paymentMethod = PaymentMethod.UNKNOWN
        var stopsCount = 1
        var passengerNote: String? = null
        var pickupAddress: String? = null
        var dropoffAddress: String? = null

        val lines = rawText.split("\n", " • ", " | ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 1. Detect Payment Method
        val fullLower = rawText.lowercase()
        when {
            fullLower.contains("كاش") || fullLower.contains("نقدي") || fullLower.contains("cash") -> {
                paymentMethod = PaymentMethod.CASH
            }
            fullLower.contains("فيزا") || fullLower.contains("بطاقة") || fullLower.contains("card") || fullLower.contains("visa") -> {
                paymentMethod = PaymentMethod.ELECTRONIC
            }
            fullLower.contains("محفظة") || fullLower.contains("wallet") -> {
                paymentMethod = PaymentMethod.WALLET
            }
        }

        // 2. Detect Stops
        if (fullLower.contains("محطتين") || fullLower.contains("محطتان") || fullLower.contains("وقفتين")) {
            stopsCount = 2
        } else if (fullLower.contains("محطة إضافية") || fullLower.contains("stop")) {
            stopsCount = 2
        } else if (fullLower.contains("3 محطات") || fullLower.contains("3 stops")) {
            stopsCount = 3
        }

        // 3. Extract Distances
        val foundDistances = mutableListOf<Double>()
        for (line in lines) {
            val kmMatcher = distanceKmRegex.matcher(line)
            while (kmMatcher.find()) {
                val distStr = kmMatcher.group(1)?.replace(",", ".")
                distStr?.toDoubleOrNull()?.let { foundDistances.add(it) }
            }

            val mMatcher = distanceMeterRegex.matcher(line)
            while (mMatcher.find()) {
                val mStr = mMatcher.group(1)
                mStr?.toDoubleOrNull()?.let { foundDistances.add(it / 1000.0) }
            }
        }

        // Distribute distances (usually first is pickup, second is trip or vice versa depending on context)
        if (foundDistances.isNotEmpty()) {
            if (foundDistances.size == 1) {
                // If only one distance is visible (common on inDrive cards or before accepting)
                val d = foundDistances[0]
                if (d < 4.0) {
                    pickupKm = d
                } else {
                    tripKm = d
                }
            } else {
                // Usually smaller/first is pickup and longer is trip
                pickupKm = foundDistances[0]
                tripKm = foundDistances[1]
            }
        }

        // 4. Extract Fare
        for (line in lines) {
            if (line.contains("ج.م") || line.contains("جنيه") || line.contains("£") || line.contains("EGP") || line.contains("LE") || line.contains("SAR") || line.contains("ر.س")) {
                val fareMatcher = fareRegex.matcher(line)
                if (fareMatcher.find()) {
                    val str = fareMatcher.group(1)?.replace(",", ".")
                    val num = str?.toDoubleOrNull()
                    if (num != null && num > 10.0 && num < 5000.0) {
                        fare = num
                        break
                    }
                }
            }
        }

        // Fallback for fare if no currency symbol attached
        if (fare == null) {
            for (line in lines) {
                val trimmed = line.replace(" ", "")
                if (trimmed.matches(Regex("""^[0-9]+(?:[.,][0-9]{1,2})?$"""))) {
                    val num = trimmed.replace(",", ".").toDoubleOrNull()
                    if (num != null && num in 20.0..2500.0) {
                        fare = num
                        break
                    }
                }
            }
        }

        // 5. Extract Passenger Rating
        for (line in lines) {
            val rMatcher = ratingRegex.matcher(line)
            if (rMatcher.find()) {
                val rVal = rMatcher.group(1)?.toDoubleOrNull()
                if (rVal != null && rVal in 3.0..5.0) {
                    rating = rVal
                    break
                }
            }
        }

        // 6. Extract Passenger Trips
        for (line in lines) {
            val tMatcher = tripsRegex.matcher(line)
            if (tMatcher.find()) {
                tripsCount = tMatcher.group(1)?.toIntOrNull()
                break
            }
        }

        // 7. Extract Order Age
        for (line in lines) {
            val ageMatcher = orderAgeRegex.matcher(line)
            if (ageMatcher.find()) {
                orderAgeSec = ageMatcher.group(1)?.toIntOrNull()
                break
            }
        }

        // 8. Extract Notes & Addresses from remaining lines
        val addressLines = lines.filter { line ->
            !line.contains("ج.م") &&
            !line.contains("كم") &&
            !line.contains("km") &&
            !line.contains("★") &&
            line.length > 3
        }

        if (addressLines.size >= 2) {
            pickupAddress = addressLines[0]
            dropoffAddress = addressLines[1]
        } else if (addressLines.isNotEmpty()) {
            dropoffAddress = addressLines[0]
        }

        // Check if there's a note line
        for (line in lines) {
            if (line.startsWith("ملاحظة:") || line.startsWith("Note:") || line.contains("تكييف") || line.contains("سجاير")) {
                passengerNote = line
                break
            }
        }

        return OrderDetails(
            app = app,
            rawText = rawText,
            fare = fare,
            pickupDistanceKm = pickupKm,
            tripDistanceKm = tripKm,
            passengerRating = rating,
            passengerTripsCount = tripsCount,
            orderAgeSeconds = orderAgeSec,
            paymentMethod = paymentMethod,
            stopsCount = stopsCount,
            pickupAddress = pickupAddress,
            dropoffAddress = dropoffAddress,
            passengerNote = passengerNote,
            isListCard = isListCard
            // No coordinates: the ride apps don't expose them as text, so we never invent any.
        )
    }
}
