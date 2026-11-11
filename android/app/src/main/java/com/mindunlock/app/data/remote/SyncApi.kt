package com.mindunlock.app.data.remote

import com.mindunlock.app.data.model.ChapterResponse
import com.mindunlock.app.data.model.SyncManifest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface SyncApi {
    @GET("api/sync/manifest")
    suspend fun getManifest(): SyncManifest

    @GET("api/sync/chapter/{chapterId}")
    suspend fun getChapter(@Path("chapterId") chapterId: String): ChapterResponse

    @GET("api/health")
    suspend fun health(): Map<String, Any?>
}

object SyncApiFactory {
    fun create(baseUrl: String): SyncApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SyncApi::class.java)
    }
}
