package com.canok.kargotycoon

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.canok.kargotycoon.data.SessionController
import com.canok.kargotycoon.data.SessionMode
import com.canok.kargotycoon.game.domain.Ownership
import com.canok.kargotycoon.game.domain.VehicleStatus
import com.canok.kargotycoon.game.engine.GameCommand
import com.canok.kargotycoon.game.engine.GameEngine
import com.canok.kargotycoon.game.engine.GameResult
import com.canok.kargotycoon.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Meaningful UI journeys driven entirely through the real [SessionController].
 * No money, progress or offers are injected; screens dispatch real engine
 * commands and the tests assert on the resulting session state.
 */
@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val engine = GameEngine()
    private lateinit var session: SessionController

    @Before
    fun prepare() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KargoApplication
        session = application.session
        runBlocking { session.startNewGame(seed = 20261001L, languageTag = "en") }
        compose.waitUntil(timeoutMillis = 15_000) { session.state.value.mode == SessionMode.PLAYING }
        waitForTag(TestTags.DASHBOARD_ROOT)
    }

    private fun waitForTag(tag: String, timeout: Long = 15_000) {
        compose.waitUntil(timeoutMillis = timeout) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun nodeCount(tag: String): Int = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun game() = requireNotNull(session.state.value.game)

    @Test
    fun launcherKeepsTheApplicationTitleVisible() {
        compose.onNodeWithTag(TestTags.APP_TITLE).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag(TestTags.APP_TITLE).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag(TestTags.DASHBOARD_ROOT).assertIsDisplayed()
    }

    @Test
    fun bottomNavigationReachesEveryPrimarySection() {
        val sections = listOf(
            TestTags.NAV_DASHBOARD to TestTags.DASHBOARD_ROOT,
            TestTags.NAV_JOBS to TestTags.JOBS_ROOT,
            TestTags.NAV_FLEET to TestTags.FLEET_ROOT,
            TestTags.NAV_TEAM to TestTags.TEAM_ROOT,
            TestTags.NAV_MORE to TestTags.MORE_ROOT,
        )
        sections.forEach { (nav, root) ->
            compose.onNodeWithTag(nav).performClick()
            waitForTag(root)
        }
        compose.onNodeWithTag(TestTags.MORE_MAP).performClick()
        waitForTag(TestTags.MAP_ROOT)
        compose.onNodeWithTag(TestTags.BACK).performClick()
        waitForTag(TestTags.MORE_ROOT)
    }

    @Test
    fun tutorialDismissesThroughTheEngineAndReappearsFromSettings() {
        compose.onNodeWithTag(TestTags.TUTORIAL_BANNER).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.TUTORIAL_DISMISS).performClick()
        compose.waitUntil(timeoutMillis = 10_000) { game().tutorial.dismissed }
        assertTrue(nodeCount(TestTags.TUTORIAL_BANNER) == 0)

        compose.onNodeWithTag(TestTags.NAV_MORE).performClick()
        waitForTag(TestTags.MORE_ROOT)
        compose.onNodeWithTag(TestTags.MORE_SETTINGS).performClick()
        waitForTag(TestTags.SETTINGS_ROOT)
        compose.onNodeWithTag(TestTags.SETTINGS_TUTORIAL).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { !game().tutorial.dismissed }
    }

    @Test
    fun languageChangePersistsThroughTheEngine() {
        compose.onNodeWithTag(TestTags.NAV_MORE).performClick()
        waitForTag(TestTags.MORE_ROOT)
        compose.onNodeWithTag(TestTags.MORE_SETTINGS).performClick()
        waitForTag(TestTags.SETTINGS_ROOT)

        compose.onNodeWithTag(TestTags.SETTINGS_LANG_TR).performClick()
        compose.waitUntil(timeoutMillis = 10_000) { game().settings.languageTag == "tr" }
        compose.onNodeWithTag(TestTags.SETTINGS_LANG_EN).performClick()
        compose.waitUntil(timeoutMillis = 10_000) { game().settings.languageTag == "en" }
    }

    @Test
    fun endingTheDayRequiresConfirmationAndAdvancesTheCalendar() {
        compose.onNodeWithTag(TestTags.DASHBOARD_END_DAY).performScrollTo().performClick()
        waitForTag(TestTags.DIALOG_CONFIRM)
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntil(timeoutMillis = 15_000) { game().gameDay >= 2 }
    }

    @Test
    fun acceptsAFeasibleOfferWithARealEngineAcceptance() {
        val feasible = feasibleOffer()
        assertTrue("expected at least one feasible offer", feasible != null)
        val offerId = requireNotNull(feasible)

        compose.onNodeWithTag(TestTags.NAV_JOBS).performClick()
        waitForTag(TestTags.JOBS_ROOT)
        compose.onNodeWithTag(TestTags.JOBS_OFFERS_LIST).performScrollToNode(hasTestTag(TestTags.offer(offerId)))
        compose.onNodeWithTag(TestTags.offer(offerId)).performClick()
        waitForTag(TestTags.JOB_DETAIL_ROOT)
        compose.waitUntil(timeoutMillis = 15_000) {
            runCatching { compose.onNodeWithTag(TestTags.JOB_ACCEPT).assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithTag(TestTags.JOB_ACCEPT).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 15_000) { game().activeJobs.size == 1 }
    }

    @Test
    fun buysHiresAndAssignsUsingOnlyRealEngineCommands() {
        earnUntil(targetCents = 130_000L, minimumJobs = 4, minimumDay = 2)

        compose.onNodeWithTag(TestTags.NAV_FLEET).performClick()
        waitForTag(TestTags.FLEET_ROOT)
        compose.onNodeWithTag(TestTags.FLEET_PURCHASE).performClick()
        waitForTag(TestTags.PURCHASE_ROOT)
        compose.onNodeWithTag(TestTags.buySpec("city-van")).performScrollTo().performClick()
        waitForTag(TestTags.DIALOG_CONFIRM)
        compose.onNodeWithTag(TestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntil(timeoutMillis = 15_000) { game().vehicles.any { it.ownership == Ownership.OWNED } }

        compose.onNodeWithTag(TestTags.NAV_TEAM).performClick()
        waitForTag(TestTags.TEAM_ROOT)
        compose.onNodeWithTag(TestTags.TEAM_HIRE).performClick()
        waitForTag(TestTags.hireTier("junior"))
        compose.onNodeWithTag(TestTags.hireTier("junior")).performClick()
        compose.onNodeWithTag(TestTags.HIRE_NAME).performClick().performTextInput("Ada")
        compose.onNodeWithTag(TestTags.HIRE_CONFIRM).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 15_000) { game().drivers.size == 1 }

        val owned = game().vehicles.first { it.ownership == Ownership.OWNED }
        val driverId = game().drivers.first().id.value
        compose.onNodeWithTag(TestTags.NAV_FLEET).performClick()
        waitForTag(TestTags.FLEET_ROOT)
        compose.onNodeWithTag(TestTags.vehicle(owned.id.value)).performScrollTo().performClick()
        waitForTag(TestTags.VEHICLE_ROOT)
        compose.onNodeWithTag(TestTags.VEHICLE_ASSIGN_DRIVER).performScrollTo().performClick()
        waitForTag(TestTags.driver(driverId))
        compose.onNodeWithTag(TestTags.driver(driverId)).performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            game().vehicles.first { it.id == owned.id }.assignedDriverId != null
        }
    }

    /** Read-only engine probe: which offer can the current fleet actually accept right now. */
    private fun feasibleOffer(): String? {
        val state = game()
        val vehicle = state.vehicles.firstOrNull { it.status == VehicleStatus.AVAILABLE && state.activeJobs.none { job -> job.vehicleId == it.id } }
            ?: return null
        return state.offers.firstNotNullOfOrNull { offer ->
            offer.routeOptions.firstNotNullOfOrNull { route ->
                val command = GameCommand.AcceptJob(SessionController.id(), offer.id, vehicle.id, route, manualDriving = true)
                offer.id.value.takeIf { engine.reduce(state, command) is GameResult.Applied }
            }
        }
    }

    /**
     * Earns funds only by dispatching real commands: accept a feasible offer,
     * advance time to its completion, and close the day when the offer board is
     * exhausted.
     */
    private fun earnUntil(targetCents: Long, minimumJobs: Int, minimumDay: Int) {
        repeat(60) {
            val state = game()
            if (state.money.cents >= targetCents && state.progression.completedJobs >= minimumJobs && state.gameDay >= minimumDay) return
            val vehicle = state.vehicles.firstOrNull { it.status == VehicleStatus.AVAILABLE && state.activeJobs.none { job -> job.vehicleId == it.id } }
            val command = if (vehicle == null) {
                null
            } else {
                state.offers.firstNotNullOfOrNull { offer ->
                    offer.routeOptions.firstNotNullOfOrNull { route ->
                        GameCommand.AcceptJob(SessionController.id(), offer.id, vehicle.id, route, manualDriving = true)
                            .takeIf { engine.reduce(state, it) is GameResult.Applied }
                    }
                }
            }
            if (command == null) {
                runBlocking { session.dispatch(GameCommand.AdvanceDay(SessionController.id())) }
            } else {
                runBlocking { session.dispatch(command) }
                val job = game().activeJobs.maxByOrNull { it.completionAt.millis }
                if (job != null) {
                    val delta = (job.completionAt.millis - game().gameTime.millis).coerceAtLeast(0L)
                    runBlocking { session.dispatch(GameCommand.AdvanceTime(SessionController.id(), delta)) }
                }
            }
        }
    }
}
