package com.lastpaycheck.data.remote

import com.lastpaycheck.data.remote.dto.CatalogManifestDto
import com.lastpaycheck.data.remote.dto.SaveRequestDto
import com.lastpaycheck.data.remote.dto.UserSaveDto
import com.lastpaycheck.data.remote.interceptor.ProtectedRpc
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

internal interface CatalogApi {
    @GET("functions/v1/catalog")
    suspend fun getManifest(): Response<CatalogManifestDto>
}

internal interface SaveApi {
    @ProtectedRpc
    @GET("rest/v1/user_save?select=*")
    suspend fun readOwnSave(): Response<List<UserSaveDto>>

    @ProtectedRpc
    @POST("rest/v1/rpc/save_game")
    suspend fun saveGame(@Body request: SaveRequestDto): Response<UserSaveDto>
}
