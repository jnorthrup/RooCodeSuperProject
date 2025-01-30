package api.rest

import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI

data class Message(val id: String, val content: String)

@KtorExperimentalAPI
fun main() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }
        install(StatusPages) {
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.localizedMessage)
            }
        }
        routing {
            route("/api") {
                get("/messages") {
                    call.respond(HttpStatusCode.OK, listOf(
                        Message("1", "Hello, World!"),
                        Message("2", "Ktor is awesome!")
                    ))
                }
                post("/messages") {
                    val message = call.receive<Message>()
                    call.respond(HttpStatusCode.Created, message)
                }
            }
        }
    }.start(wait = true)
}
