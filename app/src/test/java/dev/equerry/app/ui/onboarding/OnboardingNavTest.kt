package dev.equerry.app.ui.onboarding

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import dev.equerry.app.EquerryNavHost
import dev.equerry.app.HomeScreen
import dev.equerry.app.Route
import dev.equerry.app.startDestinationFor
import dev.equerry.app.ui.theme.EquerryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Onboarding nav + HOME setup banner (t-8, c-1/c-2/c-3): the start destination switches on the
 * completed flag, onboarding is re-enterable from HOME, and the setup banner tracks the CHAT
 * mapping. The completed-user-never-trapped clause is covered by the HOME start-destination case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingNavTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: TestNavHostController

    private fun setGraph(startDestination: String) {
        compose.setContent {
            nav = TestNavHostController(LocalContext.current)
            nav.navigatorProvider.addNavigator(ComposeNavigator())
            EquerryTheme {
                EquerryNavHost(
                    navController = nav,
                    startDestination = startDestination,
                    onboardingContent = { Text("onboarding-stub") },
                    providersContent = { Text("providers-stub") },
                    slotsContent = { Text("slots-stub") },
                    probeContent = { Text("probe-stub") },
                    chatContent = { Text("chat-stub") },
                )
            }
        }
    }

    @Test
    fun start_destination_resolves_from_the_completed_flag() {
        assertEquals(Route.ONBOARDING, startDestinationFor(onboardingCompleted = false))
        assertEquals(Route.HOME, startDestinationFor(onboardingCompleted = true))
    }

    @Test
    fun an_unonboarded_user_starts_on_onboarding() {
        setGraph(startDestination = Route.ONBOARDING)
        assertEquals(Route.ONBOARDING, nav.currentDestination?.route)
    }

    @Test
    fun a_completed_user_never_lands_on_onboarding() {
        setGraph(startDestination = Route.HOME)
        assertEquals(Route.HOME, nav.currentDestination?.route)
    }

    @Test
    fun onboarding_is_re_enterable_from_home() {
        setGraph(startDestination = Route.HOME)
        compose.onNodeWithText("Set up Equerry").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Route.ONBOARDING, nav.currentDestination?.route)
        }
    }

    @Test
    fun setup_banner_shows_while_chat_is_unmapped() {
        compose.setContent {
            EquerryTheme {
                HomeScreen(
                    onProviders = {}, onSlots = {}, onProbe = {}, onChat = {}, onVoice = {},
                    onSetup = {}, showSetupBanner = true,
                )
            }
        }
        compose.onNodeWithText("Finish setting up Equerry").assertIsDisplayed()
    }

    @Test
    fun setup_banner_is_absent_once_chat_is_mapped() {
        compose.setContent {
            EquerryTheme {
                HomeScreen(
                    onProviders = {}, onSlots = {}, onProbe = {}, onChat = {}, onVoice = {},
                    onSetup = {}, showSetupBanner = false,
                )
            }
        }
        compose.onNodeWithText("Finish setting up Equerry").assertDoesNotExist()
    }
}
