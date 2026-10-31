package com.example.model

/**
 * The only three supported ride-hailing apps as requested.
 * No other application is inspected.
 */
enum class SupportedApp(
    val titleArabic: String,
    val packageName: String,
    val defaultCommissionPct: Double
) {
    UBER("أوبر", "com.ubercab.driver", 25.0),
    DIDI("ديدي", "com.didiglobal.driver", 22.0),
    INDRIVER("إن درايف", "sinet.startup.inDriver", 12.0);

    companion object {
        fun fromPackage(pkg: String?): SupportedApp? {
            if (pkg == null) return null
            return entries.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }
        }
    }
}
