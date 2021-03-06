package org.simple.clinic.home.report

import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.reports.ReportsRepository
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent

class ReportsScreenControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val screen = mock<ReportsScreen>()

  private val uiEvents = PublishSubject.create<UiEvent>()

  private val reportsRepository: ReportsRepository = mock()
  private val controller = ReportsScreenController(reportsRepository)

  @Before
  fun setUp() {
    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when a reports file is emitted then update the screen`() {
    val reportsContent = "Reports"
    whenever(reportsRepository.reportsContentText()).thenReturn(Observable.just(Optional.of(reportsContent)))

    uiEvents.onNext(ScreenCreated())

    verify(screen).showReport(reportsContent)
  }

  @Test
  fun `screen should be updated whenever the reports file changes`() {
    val firstReport = "Reports for yesterday"
    val secondReport = "Reports for today"
    whenever(reportsRepository.reportsContentText()).thenReturn(Observable.just(
        Optional.of(firstReport),
        Optional.of(secondReport)
    ))

    uiEvents.onNext(ScreenCreated())

    val inorder = inOrder(screen)
    inorder.verify(screen).showReport(firstReport)
    inorder.verify(screen).showReport(secondReport)
  }

  @Test
  fun `when the reports file does not exist then screen should show no-reports view`() {
    whenever(reportsRepository.reportsContentText()).thenReturn(Observable.just(Optional.empty()))

    uiEvents.onNext(ScreenCreated())

    verify(screen).showNoReportsAvailable()
  }
}
