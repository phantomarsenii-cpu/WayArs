package com.wayars.app.domain.model

/** Fields scraped from the screen before they are validated/evaluated. */
data class RawOrderCandidate(
    val earnings: Double?,
    val distanceKm: Double?,
    val timeMinutes: Double?,
    val currency: Currency?
) {
    val isComplete: Boolean
        get() = earnings != null && distanceKm != null && distanceKm > 0 && timeMinutes != null && timeMinutes > 0
}
