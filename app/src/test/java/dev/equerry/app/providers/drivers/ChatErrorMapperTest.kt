package dev.equerry.app.providers.drivers

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class ChatErrorMapperTest {

    @Test
    fun maps_401_and_403_to_auth() {
        assertEquals(ChatError.Auth, ChatErrorMapper.fromHttpStatus(401))
        assertEquals(ChatError.Auth, ChatErrorMapper.fromHttpStatus(403))
    }

    @Test
    fun maps_other_non_2xx_to_http_with_code() {
        assertEquals(ChatError.Http(500), ChatErrorMapper.fromHttpStatus(500))
        assertEquals(ChatError.Http(429), ChatErrorMapper.fromHttpStatus(429))
    }

    @Test
    fun maps_connectivity_failures_to_network() {
        assertEquals(ChatError.Network, ChatErrorMapper.fromThrowable(UnknownHostException("no dns")))
        assertEquals(ChatError.Network, ChatErrorMapper.fromThrowable(IOException("socket closed")))
    }

    @Test
    fun maps_parse_failure_to_malformed() {
        assertEquals(ChatError.Malformed, ChatErrorMapper.fromThrowable(SerializationException("bad json")))
    }

    @Test
    fun mapped_message_never_contains_a_key_even_when_the_input_does() {
        val key = "sk-secret-ABCDEF1234567890"
        // An exception whose message embeds the key (e.g. a leaked URL) must not surface it.
        val fromIo = ChatErrorMapper.fromThrowable(IOException("failed to connect to https://h/?token=$key"))
        val fromParse = ChatErrorMapper.fromThrowable(SerializationException("unexpected token near $key"))
        assertFalse(fromIo.message.contains(key))
        assertFalse(fromParse.message.contains(key))
    }
}
