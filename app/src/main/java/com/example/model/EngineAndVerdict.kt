package com.example.model

/**
 * Car engine categories for realistic fuel consumption estimation:
 * - Up to 1300 cc
 * - 1400 - 1600 cc
 * - 1800 - 2000 cc
 * - Greater than 2000 cc
 */
enum class EngineSize(
    val labelArabic: String,
    val avgLitersPer100Km: Double
) {
    UP_TO_1300("حتى 1300 سي سي (اقتصادي)", 5.5),
    CC_1400_TO_1600("1400 - 1600 سي سي (متوسط)", 7.2),
    CC_1800_TO_2000("1800 - 2000 سي سي (مرتفع)", 9.2),
    ABOVE_2000("أكبر من 2000 سي سي (كبير)", 11.5)
}

enum class PaymentMethod(val labelArabic: String) {
    CASH("نقدي (كاش)"),
    ELECTRONIC("بطاقة / فيزا"),
    WALLET("محفظة إلكترونية"),
    UNKNOWN("غير محدد")
}

enum class DistanceUnit(val labelArabic: String, val shortLabel: String) {
    KILOMETER("كيلومتر", "كم"),
    MILE("ميل", "ميل")
}

enum class CalcRateType(val labelArabic: String) {
    GROSS("قبل عمولة التطبيق"),
    NET("صافي بعد العمولة والمصاريف")
}

/**
 * The 5 standard verdict colors and statuses:
 * - GREEN: Accept it (اقبله)
 * - LIGHT_GREEN: Passed visible info, check route (عدّى اللي باين افتحه وشوف المشوار)
 * - ORANGE: Think first (فكّر الأول - فاوض بكام)
 * - RED: Skip it (سيبه)
 * - GRAY: Wait for details / incomplete info (استنى التفاصيل)
 */
enum class VerdictType(
    val labelArabic: String,
    val shortBadge: String
) {
    ACCEPT(labelArabic = "اقبله", shortBadge = "قبول مميز"),
    CHECK_DETAILS(labelArabic = "عدّى اللي باين، افتحه وشوف المشوار", shortBadge = "راجع الخريطة"),
    CONSIDER(labelArabic = "فكّر الأول", shortBadge = "تفاوض"),
    REJECT(labelArabic = "سيبه", shortBadge = "تخطي"),
    WAIT_DETAILS(labelArabic = "استنى التفاصيل", shortBadge = "بيانات ناقصة")
}
