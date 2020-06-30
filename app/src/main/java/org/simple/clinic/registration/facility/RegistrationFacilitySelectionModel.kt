package org.simple.clinic.registration.facility

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.facility.Facility
import org.simple.clinic.location.LocationUpdate

@Parcelize
data class RegistrationFacilitySelectionModel(
    val searchQuery: String,
    val facilities: List<Facility>?,
    val currentLocation: LocationUpdate?,
    val totalFacilityCount: Int?
) : Parcelable {

  companion object {
    fun create(): RegistrationFacilitySelectionModel = RegistrationFacilitySelectionModel(
        searchQuery = "",
        facilities = null,
        currentLocation = null,
        totalFacilityCount = null
    )
  }

  val hasLoadedFacilities: Boolean
    get() = facilities != null && currentLocation != null

  val hasFetchedLocation: Boolean
    get() = currentLocation != null

  val hasLoadedTotalFacilityCount: Boolean
    get() = totalFacilityCount != null

  fun queryChanged(query: String): RegistrationFacilitySelectionModel {
    return copy(searchQuery = query)
  }

  fun facilitiesLoaded(facilities: List<Facility>): RegistrationFacilitySelectionModel {
    return copy(facilities = facilities)
  }

  fun locationFetched(locationUpdate: LocationUpdate): RegistrationFacilitySelectionModel {
    return copy(currentLocation = locationUpdate)
  }

  fun facilityCountLoaded(count: Int): RegistrationFacilitySelectionModel {
    return copy(totalFacilityCount = count)
  }
}
