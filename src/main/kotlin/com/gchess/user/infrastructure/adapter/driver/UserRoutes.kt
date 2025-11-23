/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.gchess.user.infrastructure.adapter.driver

import com.gchess.shared.domain.model.UserId
import com.gchess.user.application.usecase.GetUserUseCase
import com.gchess.user.infrastructure.adapter.driver.dto.toDTO
import io.bkbn.kompendium.core.metadata.GetInfo
import io.bkbn.kompendium.core.plugin.NotarizedRoute
import io.bkbn.kompendium.json.schema.definition.TypeDefinition
import io.bkbn.kompendium.oas.payload.Parameter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * HTTP routes for user operations.
 * Handles retrieving user information.
 */
fun Application.configureUserRoutes() {
    val getUserUseCase by inject<GetUserUseCase>()

    routing {
        route("/api/users") {
            // Get user by ID
            route("/{id}") {
                install(NotarizedRoute()) {
                    tags = setOf("Users")
                    get = GetInfo.builder {
                        summary("Get user by ID")
                        description("Retrieve user information by player ID")
                        parameters = listOf(
                            Parameter(
                                name = "id",
                                `in` = Parameter.Location.path,
                                schema = TypeDefinition.STRING,
                                description = "User ID (ULID format)"
                            )
                        )
                        response {
                            responseCode(HttpStatusCode.OK)
                            responseType<com.gchess.user.infrastructure.adapter.driver.dto.UserDTO>()
                            description("User found")
                        }
                        canRespond {
                            responseCode(HttpStatusCode.BadRequest)
                            responseType<Map<String, String>>()
                            description("Invalid user ID format")
                        }
                        canRespond {
                            responseCode(HttpStatusCode.NotFound)
                            responseType<Map<String, String>>()
                            description("User not found")
                        }
                    }
                }

                get {
                    val id = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing user id")
                    )

                    val playerId = try {
                        UserId.fromString(id)
                    } catch (e: Exception) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid user id format")
                        )
                    }

                    val user = getUserUseCase.execute(playerId)
                    if (user == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "User not found")
                        )
                    } else {
                        call.respond(user.toDTO())
                    }
                }
            }
        }
    }
}
