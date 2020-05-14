package org.simple.clinic.summary

interface PatientSummaryScreenUi {
  fun populatePatientProfile(patientSummaryProfile: PatientSummaryProfile)
  fun showEditButton()
  fun showDiabetesView()
  fun hideDiabetesView()
  fun showContactDoctorButtonTextAndIcon()
  fun showContactButtonProgress()
  fun enableContactDoctorButton()
  fun disableContactDoctorButton()
  fun showContactDoctorButton()
  fun hideContactDoctorButton()
}
