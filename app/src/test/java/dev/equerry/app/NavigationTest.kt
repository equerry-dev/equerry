package dev.equerry.app

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import dev.equerry.app.ui.theme.EquerryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: TestNavHostController

    private fun setGraph() {
        compose.setContent {
            nav = TestNavHostController(LocalContext.current)
            nav.navigatorProvider.addNavigator(ComposeNavigator())
            EquerryTheme {
                EquerryNavHost(
                    navController = nav,
                    providersContent = { Text("providers-stub") },
                    slotsContent = { Text("slots-stub") },
                    probeContent = { Text("probe-stub") },
                    chatContent = { Text("chat-stub") },
                )
            }
        }
    }

    @Test
    fun starts_on_the_home_destination() {
        setGraph()
        assertEquals(Route.HOME, nav.currentDestination?.route)
    }

    @Test
    fun home_navigates_to_the_provider_list_route() {
        setGraph()
        compose.onNodeWithText("Providers").performClick()
        compose.runOnIdle {
            assertEquals(Route.PROVIDERS, nav.currentDestination?.route)
        }
    }

    @Test
    fun home_navigates_to_the_capability_slots_route() {
        setGraph()
        compose.onNodeWithText("Capability slots").performClick()
        compose.runOnIdle {
            assertEquals(Route.SLOTS, nav.currentDestination?.route)
        }
    }

    @Test
    fun home_navigates_to_the_probe_log_route() {
        setGraph()
        compose.onNodeWithText("Probe log").performClick()
        compose.runOnIdle {
            assertEquals(Route.PROBE, nav.currentDestination?.route)
        }
    }

    @Test
    fun home_navigates_to_the_chat_route() {
        setGraph()
        compose.onNodeWithText("Chat").performClick()
        compose.runOnIdle {
            assertEquals(Route.CHAT, nav.currentDestination?.route)
        }
    }
}
