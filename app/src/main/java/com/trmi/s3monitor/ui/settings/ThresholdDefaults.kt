package com.trmi.s3monitor.ui.settings

/**
 * Threshold defaults and ranges for the 6 sliders on the Settings screen.
 *
 * Per the R7 update of the prompt, the Settings screen has 6 slider rows
 * (PM1 is kept per user override even though the new spec only lists 5).
 * Each row is a single horizontal line: text label on the left, slider
 * filling the remaining width on the right.
 *
 * Initial display values match the spec sketch literally:
 *   - CO2: 1501ppm
 *   - Temp: 15 | 36 (range)
 *   - RH: 30 | 80 (range)
 *   - PM1: 50 (kept)
 *   - PM2.5: 91
 *   - PM10: 91
 *
 * The values below are reasonable indoor-air ranges; the real product
 * would source them from the device's spec sheet. They live here as
 * constants so the layout XML and the activity share a single source of
 * truth for valueFrom / valueTo / default.
 *
 * Persistence keys are also defined here so the SharedPreferences layer
 * doesn't drift out of sync with the slider that owns each value.
 */
object ThresholdDefaults {

    // ---- CO2 (single) ----
    const val CO2_MIN = 400f          // ppm — outdoor baseline
    const val CO2_MAX = 5000f         // ppm — very stale indoor air
    const val CO2_DEFAULT = 1501f     // ppm — per R7 spec sketch
    const val KEY_CO2 = "threshold_co2"

    // ---- Temp (range) ----
    const val TEMP_MIN = 0f           // °C
    const val TEMP_MAX = 50f          // °C
    // R7 spec sketch: "Temp: 15 | 36".
    val TEMP_DEFAULT = floatArrayOf(15f, 36f)
    const val KEY_TEMP_START = "threshold_temp_start"
    const val KEY_TEMP_END = "threshold_temp_end"

    // ---- RH (range) ----
    const val RH_MIN = 0f             // %
    const val RH_MAX = 100f           // %
    // R7 spec sketch: "RH: 30 | 80".
    val RH_DEFAULT = floatArrayOf(30f, 80f)
    const val KEY_RH_START = "threshold_rh_start"
    const val KEY_RH_END = "threshold_rh_end"

    // ---- PM1 (single) — kept per user override (R7 spec drops it). ----
    const val PM1_MIN = 0f            // μg/m³
    const val PM1_MAX = 500f          // μg/m³
    const val PM1_DEFAULT = 50f       // μg/m³
    const val KEY_PM1 = "threshold_pm1"

    // ---- PM2.5 (single) ----
    const val PM25_MIN = 0f
    const val PM25_MAX = 500f
    // R7 spec sketch: "PM2.5: 91".
    const val PM25_DEFAULT = 91f
    const val KEY_PM25 = "threshold_pm25"

    // ---- PM10 (single) ----
    const val PM10_MIN = 0f
    const val PM10_MAX = 500f
    // R7 spec sketch: "PM10: 91".
    const val PM10_DEFAULT = 91f
    const val KEY_PM10 = "threshold_pm10"

    /** SharedPreferences file name. */
    const val PREFS_NAME = "s3_thresholds"
}
