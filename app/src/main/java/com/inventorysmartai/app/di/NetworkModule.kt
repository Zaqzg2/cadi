package com.inventorysmartai.app.di

import com.inventorysmartai.app.BuildConfig
import com.inventorysmartai.app.data.backend.BackendConfig
import com.inventorysmartai.app.data.backend.BackendCredentials
import com.inventorysmartai.app.data.backend.BackendHeadersInterceptor
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
 * The app's networking. By default every call goes to this app's own backend ([BuildConfig.BACKEND_BASE_URL]) — never
 * straight to an AI provider or a Google API — which is what keeps the provider keys and the Google OAuth client secret out
 * of the APK (see backend/README.md). The one exception is the OPTIONAL direct mode: when the person saves their own provider
 * keys and turns it on, the same client also reaches Mistral / Groq / OpenRouter (BackendHeadersInterceptor makes sure the
 * backend's app key is never sent to them).
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
    fun provideOkHttpClient(credentials: BackendCredentials): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // A free-tier server that went to sleep needs up to ~1 minute to wake before it even starts the request, and an AI
            // call may then try several providers (the server stops by itself after ~100 s). So the READ timeout is long
            // (it is the one that matters: the connection is accepted at once, the answer is what takes time), and the same
            // client is used for every call rather than a per-request override.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(190, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(BackendHeadersInterceptor(BackendConfig.host, credentials))

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BackendConfig.retrofitBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideBackendApi(retrofit: Retrofit): BackendApi = retrofit.create(BackendApi::class.java)
}
