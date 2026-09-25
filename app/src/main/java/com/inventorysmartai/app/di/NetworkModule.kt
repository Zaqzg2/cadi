package com.inventorysmartai.app.di

import com.inventorysmartai.app.BuildConfig
import com.inventorysmartai.app.data.remote.BackendApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The app's only networking — every call goes to this app's own backend
 * ([BuildConfig.BACKEND_BASE_URL]), never directly to Gemini or a Google API (see backend/
 * README.md for why: this is exactly what keeps the Gemini API key and the Google OAuth client
 * secret out of the APK).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()
    // No KotlinJsonAdapterFactory needed: every DTO uses @JsonClass(generateAdapter = true)
    // (moshi-kotlin-codegen), which registers its generated adapter automatically — reflection
    // is only needed for classes that AREN'T codegen-annotated, and this project has none.

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Document extraction can legitimately take a while (Gemini call + a multipart
            // upload) — one generous timeout for every call rather than a per-request override,
            // matching the same choice made on the backend's own outbound HttpClient.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideBackendApi(retrofit: Retrofit): BackendApi = retrofit.create(BackendApi::class.java)
}
