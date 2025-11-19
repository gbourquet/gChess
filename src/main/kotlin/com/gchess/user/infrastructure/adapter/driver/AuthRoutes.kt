package com.gchess.user.infrastructure.adapter.driver

import com.gchess.infrastructure.config.JwtConfig
import com.gchess.user.application.usecase.LoginUseCase
import com.gchess.user.application.usecase.RegisterUserUseCase
import com.gchess.user.infrastructure.adapter.driver.dto.LoginRequest
import com.gchess.user.infrastructure.adapter.driver.dto.LoginResponse
import com.gchess.user.infrastructure.adapter.driver.dto.RegisterRequest
import com.gchess.user.infrastructure.adapter.driver.dto.toCredentials
import com.gchess.user.infrastructure.adapter.driver.dto.toDTO
import io.bkbn.kompendium.core.metadata.PostInfo
import io.bkbn.kompendium.core.plugin.NotarizedRoute
import io.bkbn.kompendium.json.schema.definition.TypeDefinition
import io.bkbn.kompendium.oas.payload.Parameter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * HTTP routes for user authentication.
 * Handles registration and login.
 */
fun Application.configureAuthRoutes() {
    val registerUserUseCase by inject<RegisterUserUseCase>()
    val loginUseCase by inject<LoginUseCase>()

    routing {
        route("/api/auth") {
            // Register a new user
            route("/register") {
                install(NotarizedRoute()) {
                    tags = setOf("Authentication")
                    post = PostInfo.builder {
                        summary("Register a new user")
                        description("Create a new user account with username, email, and password")
                        request {
                            requestType<RegisterRequest>()
                            description("User registration information")
                        }
                        response {
                            responseCode(HttpStatusCode.Created)
                            responseType<com.gchess.user.infrastructure.adapter.driver.dto.UserDTO>()
                            description("Successfully created user")
                        }
                        canRespond {
                            responseCode(HttpStatusCode.BadRequest)
                            responseType<Map<String, String>>()
                            description("Registration failed (username/email already taken, invalid password, etc.)")
                        }
                    }
                }

                post {
                    val request = call.receive<RegisterRequest>()

                    val result = registerUserUseCase.execute(
                        username = request.username,
                        email = request.email,
                        plainPassword = request.password
                    )

                    result.fold(
                        onSuccess = { user ->
                            call.respond(HttpStatusCode.Created, user.toDTO())
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Registration failed"))
                            )
                        }
                    )
                }
            }

            // Login
            route("/login") {
                install(NotarizedRoute()) {
                    tags = setOf("Authentication")
                    post = PostInfo.builder {
                        summary("Login")
                        description("Authenticate a user and receive a JWT token")
                        request {
                            requestType<LoginRequest>()
                            description("User credentials")
                        }
                        response {
                            responseCode(HttpStatusCode.OK)
                            responseType<LoginResponse>()
                            description("Successfully authenticated - returns user info and JWT token")
                        }
                        canRespond {
                            responseCode(HttpStatusCode.Unauthorized)
                            responseType<Map<String, String>>()
                            description("Invalid credentials")
                        }
                    }
                }

                post {
                    val request = call.receive<LoginRequest>()

                    val result = loginUseCase.execute(request.toCredentials())

                    result.fold(
                        onSuccess = { user ->
                            // Generate JWT token for the authenticated user
                            val token = JwtConfig.generateToken(user.id)

                            call.respond(
                                HttpStatusCode.OK,
                                LoginResponse(
                                    user = user.toDTO(),
                                    token = token
                                )
                            )
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to (error.message ?: "Login failed"))
                            )
                        }
                    )
                }
            }
        }
    }
}
