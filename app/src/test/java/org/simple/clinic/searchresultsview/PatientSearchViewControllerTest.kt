package org.simple.clinic.searchresultsview

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.PatientToFacilityId
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.TestData
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.PatientSearchCriteria
import org.simple.clinic.patient.PatientSearchCriteria.Name
import org.simple.clinic.patient.PatientSearchCriteria.PhoneNumber
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.widgets.UiEvent
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class PatientSearchViewControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val ui = mock<PatientSearchUi>()
  private val patientRepository = mock<PatientRepository>()
  private val userSession = mock<UserSession>()
  private val facilityRepository = mock<FacilityRepository>()
  private val bloodPressureDao = mock<BloodPressureMeasurement.RoomDao>()

  private val controller = PatientSearchViewController(
      patientRepository = patientRepository,
      userSession = userSession,
      facilityRepository = facilityRepository,
      bloodPressureDao = bloodPressureDao
  )
  private val uiEvents = PublishSubject.create<UiEvent>()

  private val currentFacility = TestData.facility(UUID.fromString("69cf85c8-6788-4071-b985-0536ae606b70"))
  private val otherFacility = TestData.facility(UUID.fromString("0bb48f0a-3a6c-4e35-8781-74b074443f36"))
  private val user = TestData.loggedInUser()

  private val patientName = "name"
  private val phoneNumber: String = "123456"

  @Before
  fun setUp() {
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }

    whenever(userSession.requireLoggedInUser()).doReturn(Observable.just(user))
    whenever(facilityRepository.currentFacility(user)).doReturn(Observable.just(currentFacility))
    whenever(patientRepository.search(Name(patientName))).doReturn(Observable.never())
    whenever(patientRepository.search(PhoneNumber(phoneNumber))).doReturn(Observable.never())
    uiEvents.compose(controller).subscribe { uiChange -> uiChange(ui) }
  }

  @Test
  fun `when search result clicked then SearchResultClicked event should be emitted`() {
    val searchResult = TestData.patientSearchResult()
    val searchResultClicked = SearchResultClicked(searchResult.uuid)
    uiEvents.onNext(searchResultClicked)

    verify(ui).searchResultClicked(searchResultClicked)
  }

  @Test
  @Parameters(method = "params for search criteria")
  fun `when register new patient clicked then RegisterNewPatient event should be emitted`(criteria: PatientSearchCriteria) {
    screenCreated()
    uiEvents.onNext(SearchPatientWithCriteria(criteria))
    uiEvents.onNext(RegisterNewPatientClicked)

    verify(ui).registerNewPatient(RegisterNewPatient(criteria))
  }

  @Test
  @Parameters(method = "params for search criteria")
  fun `when searching patients returns results, the results should be displayed`(searchCriteria: PatientSearchCriteria) {
    // given
    val searchResult1InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("1d5f18d9-43f7-4e7f-92d3-a4f641709470"),
        fullName = "Anish Acharya in CHC Bagta"
    )
    val searchResult2InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("6aba53eb-c8dd-452c-b640-c11c6df94f8a"),
        fullName = "Deepa in CHC Bagta"
    )
    val searchResult3InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("65965892-7218-4ca1-bfca-e7b037751802"),
        fullName = "Vinay in CHC Bagta"
    )
    val searchResult1InOtherFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("139bfac5-1adc-43fa-9406-d1000fb67a88"),
        fullName = "Sanchita in CHC Ballianwali"
    )
    val searchResult2InOtherFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("760c7342-b0f2-4130-9d4c-c5f18336d4b8"),
        fullName = "Daniel in CHC Ballianwali"
    )

    val allSearchResults = listOf(
        searchResult1InCurrentFacility,
        searchResult2InOtherFacility,
        searchResult3InCurrentFacility,
        searchResult2InCurrentFacility,
        searchResult1InOtherFacility
    )
    val patientUuids = allSearchResults.map { it.uuid }
    val patientAndFacilityIdsToReturn = listOf(
        PatientToFacilityId(patientUuid = searchResult1InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult2InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult3InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult1InOtherFacility.uuid, facilityUuid = otherFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult2InOtherFacility.uuid, facilityUuid = otherFacility.uuid)
    )

    whenever(bloodPressureDao.patientToFacilityIds(patientUuids))
        .doReturn(Flowable.just(patientAndFacilityIdsToReturn))
    whenever(patientRepository.search(searchCriteria))
        .doReturn(Observable.just(allSearchResults))

    // when
    screenCreated()
    uiEvents.onNext(SearchPatientWithCriteria(searchCriteria))

    // then
    val expectedSearchResults = PatientSearchResults(
        visitedCurrentFacility = listOf(searchResult1InCurrentFacility, searchResult3InCurrentFacility, searchResult2InCurrentFacility),
        notVisitedCurrentFacility = listOf(searchResult2InOtherFacility, searchResult1InOtherFacility),
        currentFacility = currentFacility
    )
    verify(ui).updateSearchResults(expectedSearchResults)
  }

  @Suppress("Unused")
  private fun `params for search criteria`(): List<PatientSearchCriteria> = listOf(
      Name(patientName = patientName),
      PhoneNumber(phoneNumber = phoneNumber)
  )

  private fun screenCreated() {
    uiEvents.onNext(SearchResultsViewCreated)
  }
}
