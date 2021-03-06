package org.simple.clinic.registration.facility

import android.annotation.SuppressLint
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.ofType
import org.simple.clinic.ReplayUntilScreenIsDestroyed
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.facility.FacilityPullResult
import org.simple.clinic.facility.FacilityPullResult.NetworkError
import org.simple.clinic.facility.FacilityPullResult.Success
import org.simple.clinic.facility.FacilityPullResult.UnexpectedError
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.facility.FacilitySync
import org.simple.clinic.facility.change.FacilitiesUpdateType.FIRST_UPDATE
import org.simple.clinic.facility.change.FacilitiesUpdateType.SUBSEQUENT_UPDATE
import org.simple.clinic.facility.change.FacilityListItemBuilder
import org.simple.clinic.location.LocationUpdate.Available
import org.simple.clinic.location.LocationUpdate.Unavailable
import org.simple.clinic.location.ScreenLocationUpdates
import org.simple.clinic.registration.RegistrationConfig
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Just
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

typealias Ui = RegistrationFacilitySelectionScreen
typealias UiChange = (Ui) -> Unit

class RegistrationFacilitySelectionScreenController @Inject constructor(
    private val facilitySync: FacilitySync,
    private val facilityRepository: FacilityRepository,
    private val userSession: UserSession,
    private val configProvider: Single<RegistrationConfig>,
    private val listItemBuilder: FacilityListItemBuilder,
    private val screenLocationUpdates: ScreenLocationUpdates
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = ReplayUntilScreenIsDestroyed(events)
        .compose(fetchLocation())
        .compose(ReportAnalyticsEvents())
        .replay()

    return Observable.mergeArray(
        fetchFacilities(replayedEvents),
        showFacilities(replayedEvents),
        toggleSearchFieldInToolbar(replayedEvents),
        proceedOnFacilityClicks(replayedEvents),
        proceedOnFacilityConfirmation(replayedEvents))
  }

  @SuppressLint("MissingPermission")
  private fun fetchLocation() = ObservableTransformer<UiEvent, UiEvent> { events ->
    val locationUpdates = Observables
        .combineLatest(events.ofType<ScreenCreated>(), configProvider.toObservable()) { _, config -> config }
        .switchMap { config ->
          screenLocationUpdates.streamUserLocation(
              updateInterval = config.locationUpdateInterval,
              timeout = config.locationListenerExpiry,
              discardOlderThan = config.staleLocationThreshold
          )
        }
        .take(1)
        .map(::RegistrationFacilityUserLocationUpdated)

    events.mergeWith(locationUpdates)
  }

  private fun fetchFacilities(events: Observable<UiEvent>): Observable<UiChange> {
    val fetchFacilitiesOnStart = events
        .ofType<ScreenCreated>()
        .flatMap { facilityRepository.recordCount() }
        .take(1)
        .flatMapSingle { count ->
          when (count) {
            0 -> facilitySync.pullWithResult()
            else -> Single.just(FacilityPullResult.Success)
          }
        }

    val fetchFacilitiesOnRetry = events
        .ofType<RegistrationFacilitySelectionRetryClicked>()
        .switchMap { facilitySync.pullWithResult().toObservable() }

    val fetchFacilities = fetchFacilitiesOnStart.mergeWith(fetchFacilitiesOnRetry)

    val locationUpdates = events.ofType<RegistrationFacilityUserLocationUpdated>()

    // We don't care about the location here. We just want to show
    // progress until we receive an update, even if it's empty.
    return Observables.combineLatest(fetchFacilities, locationUpdates)
        .flatMap { (facilityPullResult, _) ->
          when (facilityPullResult) {
            is Success -> Observable.just(
                { ui: Ui -> ui.hideProgressIndicator() })
            is NetworkError -> Observable.just(
                { ui: Ui -> ui.hideProgressIndicator() },
                { ui: Ui -> ui.showNetworkError() })
            is UnexpectedError -> Observable.just(
                { ui: Ui -> ui.hideProgressIndicator() },
                { ui: Ui -> ui.showUnexpectedError() })
          }
        }
        .startWith(Observable.just(
            { ui: Ui -> ui.hideError() },
            { ui: Ui -> ui.showProgressIndicator() }))
  }

  private fun showFacilities(events: Observable<UiEvent>): Observable<UiChange> {
    val searchQueryChanges = events
        .ofType<RegistrationFacilitySearchQueryChanged>()
        .map { it.query }

    val locationUpdates = events
        .ofType<RegistrationFacilityUserLocationUpdated>()
        .map { it.location }

    val filteredFacilityListItems = Observables
        .combineLatest(searchQueryChanges, locationUpdates, configProvider.toObservable())
        .switchMap { (query, locationUpdate, config) ->
          val userLocation = when (locationUpdate) {
            is Available -> locationUpdate.location
            is Unavailable -> null
          }

          facilityRepository
              .facilities(query)
              .map {
                listItemBuilder.build(
                    facilities = it,
                    searchQuery = query,
                    userLocation = userLocation,
                    proximityThreshold = config.proximityThresholdForNearbyFacilities)
              }
        }
        .replay()
        .refCount()

    val firstUpdate = filteredFacilityListItems
        .map { listItems -> listItems to FIRST_UPDATE }
        .take(1)

    val subsequentUpdates = filteredFacilityListItems
        .map { listItems -> listItems to SUBSEQUENT_UPDATE }
        .skip(1)

    return Observable.merge(firstUpdate, subsequentUpdates)
        .map { (listItems, updateType) ->
          { ui: Ui -> ui.updateFacilities(listItems, updateType) }
        }
  }

  private fun toggleSearchFieldInToolbar(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<ScreenCreated>()
        .flatMap { facilityRepository.recordCount() }
        .map { count -> count > 0 }
        .distinctUntilChanged()
        .map { hasFacilities ->
          if (hasFacilities) {
            { ui: Ui -> ui.showToolbarWithSearchField() }
          } else {
            { ui: Ui -> ui.showToolbarWithoutSearchField() }
          }
        }
  }

  private fun proceedOnFacilityClicks(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<RegistrationFacilityClicked>()
        .map { it.facility }
        .map { facility -> { ui: Ui -> ui.showConfirmFacilitySheet(facility.uuid, facility.name) } }
  }

  private fun proceedOnFacilityConfirmation(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<RegistrationFacilityConfirmed>()
        .map { it.facilityUuid }
        .map { facilityUuid ->
          val entry = (userSession.ongoingRegistrationEntry() as Just).value
          entry.copy(facilityId = facilityUuid)
        }
        .doOnNext(userSession::saveOngoingRegistrationEntry)
        .flatMap {
          userSession
              .saveOngoingRegistrationEntryAsUser()
              .andThen(Observable.just { ui: Ui -> ui.openIntroVideoScreen() })
        }
  }
}
