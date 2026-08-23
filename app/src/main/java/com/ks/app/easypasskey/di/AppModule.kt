package com.ks.app.easypasskey.di

import android.content.Context
import com.ks.app.easypasskey.R
import com.ks.app.easypasskey.data.Auth0Config
import com.ks.app.easypasskey.data.network.Auth0TokenApi
import com.ks.app.easypasskey.data.repository.DefaultAuthRepository
import com.ks.app.easypasskey.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAuth0Config(@ApplicationContext context: Context): Auth0Config = Auth0Config(
        domain = context.getString(R.string.com_auth0_domain),
        clientId = context.getString(R.string.com_auth0_client_id),
        scheme = context.getString(R.string.com_auth0_scheme),
        packageName = context.packageName
    )

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideAuth0TokenApi(config: Auth0Config, json: Json): Auth0TokenApi =
        Retrofit.Builder()
            .baseUrl("https://${config.domain}/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Auth0TokenApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository
}
