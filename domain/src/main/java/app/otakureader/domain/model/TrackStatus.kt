package app.otakureader.domain.model

/**
 * Reading status on an external tracking service.
 */
enum class TrackStatus(val displayName: String) {
    READING("Reading"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
    PLAN_TO_READ("Plan to Read"),
    REPEATING("Re-reading");

    companion object {
        fun fromName(name: String): TrackStatus =
            entries.find { it.name == name } ?: READING
    }
}
