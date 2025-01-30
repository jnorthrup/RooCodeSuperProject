package api.websocket

import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(DefaultHeaders)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(WebSockets)
        install(StatusPages)

        routing {
            webSocket("/ws") {
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText = frame.readText()
                    val message = Json.decodeFromString(Message.serializer(), receivedText)
                    val response = handleMessage(message)
                    outgoing.send(Frame.Text(Json.encodeToString(Message.serializer(), response)))
                }
            }
        }
    }.start(wait = true)
}

@Serializable
data class Message(val type: String, val content: String)

fun handleMessage(message: Message): Message {
    return when (message.type) {
        "greeting" -> Message("response", "Hello, ${message.content}!")
        else -> Message("error", "Unknown message type: ${message.type}")
    }
}
