package org.simple.clinic.login.pin

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.view.detaches
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.screen_login_pin.view.*
import org.simple.clinic.bindUiToController
import org.simple.clinic.di.injector
import org.simple.clinic.home.HomeScreenKey
import org.simple.clinic.router.screen.BackPressInterceptCallback
import org.simple.clinic.router.screen.BackPressInterceptor
import org.simple.clinic.router.screen.RouterDirection
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.security.pin.PinAuthenticated
import org.simple.clinic.security.pin.verification.LoginPinServerVerificationMethod.*
import org.simple.clinic.user.OngoingLoginEntry
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

class LoginPinScreen(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var controller: LoginPinScreenController

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }

    context.injector<Injector>().inject(this)

    pinEntryCardView.setForgotButtonVisible(false)

    bindUiToController(
        ui = this,
        events = Observable.mergeArray(
            screenCreates(),
            pinAuthentications(),
            backClicks(),
            otpReceived()
        ),
        controller = controller,
        screenDestroys = detaches().map { ScreenDestroyed() }
    )
  }

  private fun screenCreates(): Observable<UiEvent> {
    return Observable.just(PinScreenCreated())
  }

  private fun pinAuthentications(): Observable<UiEvent> {
    return pinEntryCardView
        .downstreamUiEvents
        .ofType<PinAuthenticated>()
        .map { mapUserDataToLoginEntry(it.data as UserData) }
        .map(::LoginPinAuthenticated)
  }

  private fun mapUserDataToLoginEntry(userData: UserData): OngoingLoginEntry {
    return OngoingLoginEntry(
        uuid = userData.uuid,
        fullName = userData.fullName,
        phoneNumber = userData.phoneNumber,
        pin = userData.pin,
        pinDigest = userData.pinDigest,
        registrationFacilityUuid = userData.registrationFacilityUuid,
        status = userData.status,
        createdAt = userData.createdAt,
        updatedAt = userData.updatedAt
    )
  }

  private fun backClicks(): Observable<PinBackClicked> {
    val backClicksFromView = backButton.clicks().map { PinBackClicked() }

    val backClicksFromSystem = Observable.create<PinBackClicked> { emitter ->
      val backPressInterceptor = object : BackPressInterceptor {
        override fun onInterceptBackPress(callback: BackPressInterceptCallback) {
          emitter.onNext(PinBackClicked())
        }
      }

      screenRouter.registerBackPressInterceptor(backPressInterceptor)

      emitter.setCancellable { screenRouter.unregisterBackPressInterceptor(backPressInterceptor) }
    }

    return backClicksFromView.mergeWith(backClicksFromSystem)
  }

  private fun otpReceived(): Observable<LoginPinOtpReceived>? {
    val key = screenRouter.key<LoginPinScreenKey>(this)
    return Observable.just(LoginPinOtpReceived(key.otp))
  }

  fun showPhoneNumber(phoneNumber: String) {
    phoneNumberTextView.text = phoneNumber
  }

  fun openHomeScreen() {
    screenRouter.clearHistoryAndPush(HomeScreenKey(), RouterDirection.REPLACE)
  }

  fun goBackToRegistrationScreen() {
    screenRouter.pop()
  }

  interface Injector {
    fun inject(target: LoginPinScreen)
  }
}
