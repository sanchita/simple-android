package org.simple.clinic.registration.register

import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.user.User
import org.simple.clinic.user.User.LoggedInStatus.NOT_LOGGED_IN
import org.simple.clinic.user.UserSession
import org.simple.clinic.user.UserStatus.WaitingForApproval
import org.simple.clinic.user.registeruser.RegisterUser
import org.simple.clinic.user.registeruser.RegistrationResult
import org.simple.clinic.user.registeruser.RegistrationResult.NetworkError
import org.simple.clinic.user.registeruser.RegistrationResult.Success
import org.simple.clinic.user.registeruser.RegistrationResult.UnexpectedError
import org.simple.clinic.util.Just
import org.simple.clinic.util.Optional
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import java.util.UUID

class RegistrationLoadingScreenControllerTest {

  private val userSession = mock<UserSession>()
  private val ui = mock<RegistrationLoadingUi>()
  private val registerUser = mock<RegisterUser>()
  private val facilityRepository = mock<FacilityRepository>()
  private val uiEvents = PublishSubject.create<UiEvent>()

  private val user = TestData.loggedInUser(
      uuid = UUID.fromString("fe1786be-5725-45b5-a6aa-e9ce0f99f794"),
      loggedInStatus = NOT_LOGGED_IN,
      status = WaitingForApproval
  )
  private val facility = TestData.facility(UUID.fromString("37e253a9-8a8a-4c60-8aac-34338dc47e8b"))

  private lateinit var controllerSubscription: Disposable

  @After
  fun tearDown() {
    controllerSubscription.dispose()
  }

  @Test
  fun `when retry button is clicked, then user registration should be attempted again`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)).doReturn(
        Single.just<RegistrationResult>(NetworkError),
        Single.just<RegistrationResult>(Success)
    )

    // when
    setupController()

    // then
    verify(ui).showNetworkError()
    verifyNoMoreInteractions(ui)
    verify(userSession, never()).clearOngoingRegistrationEntry()

    // when
    clearInvocations(ui)
    uiEvents.onNext(RegisterErrorRetryClicked)

    // then
    verify(userSession).clearOngoingRegistrationEntry()
    verify(ui).openHomeScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when screen is created, then the user registration api should be attempted`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.never()

    // when
    setupController()

    // then
    verify(registerUser).registerUserAtFacility(user, facility)
    verifyZeroInteractions(ui)
  }

  @Test
  fun `when the user registration succeeds, then clear registration entry and go to home screen`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(Success)

    // when
    setupController()

    // then
    verify(userSession).clearOngoingRegistrationEntry()
    verify(ui).openHomeScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the user registration fails with a network error, show the network error message`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(NetworkError)

    // when
    setupController()

    // then
    verify(ui).showNetworkError()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the user registration fails with any other error, show the generic error message`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(UnexpectedError)

    // when
    setupController()

    // then
    verify(ui).showUnexpectedError()
    verifyNoMoreInteractions(ui)
  }

  private fun setupController() {
    val controller = RegistrationLoadingScreenController(userSession, facilityRepository, registerUser)

    controllerSubscription = uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(ui) }

    uiEvents.onNext(ScreenCreated())
  }
}
