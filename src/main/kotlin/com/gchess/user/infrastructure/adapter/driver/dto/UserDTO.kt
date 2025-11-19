package com.gchess.user.infrastructure.adapter.driver.dto

import com.gchess.user.domain.model.Credentials
import com.gchess.user.domain.model.User
import kotlinx.serialization.Serializable

@Serializable
data class UserDTO(
    val id: String,
    val username: String,
    val email: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val user: UserDTO,
    val token: String,
    val message: String = "Login successful"
)

// Extension functions to convert between domain models and DTOs
fun User.toDTO(): UserDTO = UserDTO(
    id = id.value,
    username = username,
    email = email
    // Note: passwordHash is intentionally NOT exposed in the DTO
)

fun LoginRequest.toCredentials(): Credentials = Credentials(
    username = username,
    password = password
)
