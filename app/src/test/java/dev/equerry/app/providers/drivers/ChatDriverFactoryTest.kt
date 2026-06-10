package dev.equerry.app.providers.drivers

import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderType
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Test

class ChatDriverFactoryTest {

    private val client = OkHttpClient()
    private val openAi = OpenAiChatDriver(client)
    private val anthropic = AnthropicChatDriver(client)
    private val ollama = OllamaChatDriver(client)
    private val factory = ChatDriverFactory(openAi, anthropic, ollama)

    private fun profile(type: ProviderType) =
        ProviderProfile("id", "L", type, "https://example.com", "m")

    @Test
    fun openRouterUsesOpenAiDriver() {
        assertSame(openAi, factory.forProfile(profile(ProviderType.OPENROUTER)))
        assertSame(
            factory.forProfile(profile(ProviderType.OPENAI_COMPATIBLE)),
            factory.forProfile(profile(ProviderType.OPENROUTER)),
        )
    }

    @Test
    fun each_type_resolves_to_its_own_driver() {
        assertSame(anthropic, factory.forProfile(profile(ProviderType.ANTHROPIC)))
        assertSame(ollama, factory.forProfile(profile(ProviderType.OLLAMA)))
        assertSame(openAi, factory.forProfile(profile(ProviderType.OPENAI_COMPATIBLE)))
    }
}
