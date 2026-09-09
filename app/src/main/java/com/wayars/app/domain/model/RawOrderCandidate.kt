package com.wayars.app.domain.model

/** Fields scraped from the screen before they are validated/evaluated. */
data class RawOrderCandidate(
    val earnings: Double?,
    val distanceKm: Double?,
    val timeMinutes: Double?,
    val currency: Currency?
) {
    /**
     * timeMinutes is deliberately NOT required here. Some apps (Stuart
     * confirmed on-device) show earnings/distance in a layout our regexes
     * catch fine but never expose a parseable minutes figure — with time
     * required, those orders sat at isComplete=false forever and the
     * overlay never showed. Earnings + distance are enough to evaluate an
     * order (see EvaluateOrderUseCase); a missing timeMinutes is defaulted
     * to 0 by callers before evaluation instead of blocking the candidate.
     */
    val isComplete: Boolean
        get() = earnings != null && earnings > 0 && distanceKm != null && distanceKm > 0
}
