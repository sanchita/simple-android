package org.simple.clinic.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.cardview.widget.CardView
import kotlinx.android.synthetic.main.view_patient_search_result.view.*
import org.simple.clinic.R
import org.simple.clinic.main.TheActivity
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.DateOfBirth
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.patient.displayIconRes
import org.simple.clinic.patient.displayLetterRes
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.toLocalDateAtZone
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

class PatientSearchResultItemView(
    context: Context,
    attributeSet: AttributeSet
) : CardView(context, attributeSet) {

  @field:[Inject Named("full_date")]
  lateinit var dateTimeFormatter: DateTimeFormatter

  @field:Inject
  lateinit var userClock: UserClock

  override fun onFinishInflate() {
    super.onFinishInflate()
    inflate(context, R.layout.view_patient_search_result, this)
    if (isInEditMode) {
      return
    }
    TheActivity.component.inject(this)
  }

  fun render(model: PatientSearchResultViewModel, currentFacilityUuid: UUID) {
    renderPatientNameAgeAndGender(model.fullName, model.gender, DateOfBirth.fromPatientSearchResultViewModel(model, userClock))
    renderPatientAddress(model.address)
    renderPatientDateOfBirth(model.dateOfBirth)
    renderPatientPhoneNumber(model.phoneNumber)
    renderLastSeen(model.lastSeen, currentFacilityUuid)
  }

  private fun renderLastSeen(
      lastSeen: PatientSearchResult.LastSeen?,
      currentFacilityUuid: UUID
  ) {
    lastSeenContainer.visibleOrGone(lastSeen != null)
    if (lastSeen == null) {
      lastSeenContainer.visibility = View.GONE
    } else {
      lastSeenContainer.visibility = View.VISIBLE

      val lastSeenDate = lastSeen.lastSeenOn.toLocalDateAtZone(userClock.zone)
      val formattedLastSeenDate = dateTimeFormatter.format(lastSeenDate)

      val isCurrentFacility = lastSeen.lastSeenAtFacilityUuid == currentFacilityUuid
      if (isCurrentFacility) {
        lastSeenTextView.text = formattedLastSeenDate
      } else {
        lastSeenTextView.text = resources.getString(
            R.string.patientsearchresults_item_last_seen_date_with_facility,
            formattedLastSeenDate,
            lastSeen.lastSeenAtFacilityName)
      }
    }
  }

  private fun renderPatientPhoneNumber(phoneNumber: String?) {
    phoneNumberContainer.visibleOrGone(phoneNumber.isNullOrBlank().not())
    if (phoneNumber != null) {
      phoneNumberTextView.text = phoneNumber
    }
  }

  private fun renderPatientDateOfBirth(dateOfBirth: LocalDate?) {
    dateOfBirthContainer.visibleOrGone(dateOfBirth != null)
    if (dateOfBirth != null) {
      dateOfBirthTextView.text = dateTimeFormatter.format(dateOfBirth)
    }
  }

  private fun renderPatientAddress(address: PatientAddress) {
    val addressFields = listOf(
        address.streetAddress,
        address.colonyOrVillage,
        address.district,
        address.state,
        address.zone
    ).filterNot { it.isNullOrBlank() }

    addressLabel.text = addressFields.joinToString()
  }

  private fun renderPatientNameAgeAndGender(fullName: String, gender: Gender, dateOfBirth: DateOfBirth) {
    genderLabel.setImageResource(gender.displayIconRes)

    val ageValue = dateOfBirth.estimateAge(userClock)

    val genderLetter = resources.getString(gender.displayLetterRes)
    patientNameAgeGenderLabel.text = resources.getString(R.string.patientsummary_toolbar_title, fullName, genderLetter, ageValue.toString())
  }

  data class PatientSearchResultViewModel(
      val uuid: UUID,
      val fullName: String,
      val gender: Gender,
      val age: Age?,
      val dateOfBirth: LocalDate?,
      val address: PatientAddress,
      val phoneNumber: String?,
      val lastSeen: PatientSearchResult.LastSeen?
  )
}
