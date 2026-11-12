package com.lastpaycheck.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class SaveRequestDto(val args: SaveArgsDto)

@Serializable
internal data class SaveArgsDto(val snapshot: JsonObject, val expectedRevision: Int)

@Serializable
internal data class UserSaveDto(
    @SerialName("user_id") val userId: String,
    val snapshot: JsonObject,
    val revision: Int,
    @SerialName("updated_at") val updatedAt: String
)
