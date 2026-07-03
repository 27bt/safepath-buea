package com.safepathbuea.app.data

enum class HazardType(val id: String, val label: String) {
    POTHOLE("pothole", "Pothole"),
    OPEN_GUTTER("open_gutter", "Open gutter"),
    STEEP_SLOPE("steep_slope", "Steep slope"),
    CONSTRUCTION("construction", "Construction"),
    BLOCKED_PATH("blocked_path", "Blocked path"),
    TRAFFIC("traffic", "Traffic"),
    CROWD("crowd", "Crowd"),
    OTHER("other", "Other"),
}
