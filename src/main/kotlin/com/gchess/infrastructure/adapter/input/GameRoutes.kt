package com.gchess.infrastructure.adapter.input

import com.gchess.application.usecase.CreateGameUseCase
import com.gchess.application.usecase.GetGameUseCase
import com.gchess.application.usecase.MakeMoveUseCase
import com.gchess.domain.model.Move
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class MoveRequest(val from: String, val to: String, val promotion: String? = null)

fun Application.configureGameRoutes() {
    val createGameUseCase by inject<CreateGameUseCase>()
    val getGameUseCase by inject<GetGameUseCase>()
    val makeMoveUseCase by inject<MakeMoveUseCase>()

    routing {
        route("/api/games") {
            post {
                val game = createGameUseCase.execute()
                call.respond(HttpStatusCode.Created, game)
            }

            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing game id"
                )
                val game = getGameUseCase.execute(id)
                if (game == null) {
                    call.respond(HttpStatusCode.NotFound, "Game not found")
                } else {
                    call.respond(game)
                }
            }

            post("/{id}/moves") {
                val id = call.parameters["id"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing game id"
                )
                val moveRequest = call.receive<MoveRequest>()
                val move = Move.fromAlgebraic("${moveRequest.from}${moveRequest.to}")

                val result = makeMoveUseCase.execute(id, move)
                result.fold(
                    onSuccess = { game -> call.respond(game) },
                    onFailure = { error -> call.respond(HttpStatusCode.BadRequest, error.message ?: "Invalid move") }
                )
            }
        }
    }
}
