package org.simple.clinic.registration.facility

import org.threeten.bp.Duration

sealed class RegistrationFacilitySelectionEffect

data class FetchCurrentLocation(
    val updateInterval: Duration,
    val timeout: Duration,
    val discardOlderThan: Duration
): RegistrationFacilitySelectionEffect()
