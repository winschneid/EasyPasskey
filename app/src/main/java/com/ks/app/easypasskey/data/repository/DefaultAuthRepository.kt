package com.ks.app.easypasskey.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.auth0.android.result.Credentials
import com.ks.app.easypasskey.data.Auth0Config
import com.ks.app.easypasskey.data.local.CredentialsStore
import com.ks.app.easypasskey.data.network.Auth0TokenApi
import com.ks.app.easypasskey.data.network.TokenResponse
import com.ks.app.easypasskey.data.pkce.Pkce
import com.ks.app.easypasskey.data.sdk.Auth0WebAuthDataSource
import com.ks.app.easypasskey.domain.model.AuthCredentials
import com.ks.app.easypasskey.domain.model.AuthFlowException
import com.ks.app.easypasskey.domain.model.WebAuthorization
import com.ks.app.easypasskey.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAuthRepository @Inject constructor(
    private val config: Auth0Config,
    private val webAuthDataSource: Auth0WebAuthDataSource,
    private val tokenApi: Auth0TokenApi,
    private val credentialsStore: CredentialsStore,
    private val json: Json
) : AuthRepository {

    override val credentials: Flow<AuthCredentials?> = credentialsStore.credentials

    override suspend fun loginWithBrowser(context: Context, isSignup: Boolean): AuthCredentials {
        val result = webAuthDataSource.login(context, isSignup)
        return result.toDomain().also { credentialsStore.save(it) }
    }

    override fun createWebAuthorization(isSignup: Boolean): WebAuthorization {
        val codeVerifier = Pkce.generateCodeVerifier()
        val state = Pkce.generateState()
        val scope = if (isSignup) "openid profile email" else "openid profile email offline_access"
        val builder = Uri.Builder()
            .scheme("https")
            .authority(config.domain)
            .path("/authorize")
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", Pkce.codeChallengeS256(codeVerifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "login")
        if (isSignup) {
            builder.appendQueryParameter("screen_hint", "signup")
        }
        return WebAuthorization(
            authorizeUrl = builder.build().toString(),
            redirectUri = config.redirectUri,
            codeVerifier = codeVerifier,
            state = state
        )
    }

    override suspend fun completeWebAuthorization(
        authorization: WebAuthorization,
        redirectUrl: String
    ): AuthCredentials {
        val code = extractAuthorizationCode(authorization, redirectUrl)
        val response = try {
            tokenApi.exchangeAuthorizationCode(
                clientId = config.clientId,
                code = code,
                codeVerifier = authorization.codeVerifier,
                redirectUri = authorization.redirectUri
            )
        } catch (e: HttpException) {
            throw AuthFlowException(parseTokenErrorMessage(e))
        }
        return response.toDomain().also { credentialsStore.save(it) }
    }

    override suspend fun logout() {
        credentialsStore.clear()
    }

    private fun extractAuthorizationCode(
        authorization: WebAuthorization,
        redirectUrl: String
    ): String {
        val uri = redirectUrl.toUri()
        uri.getQueryParameter("error")?.let { error ->
            throw AuthFlowException(uri.getQueryParameter("error_description") ?: error)
        }
        if (uri.getQueryParameter("state") != authorization.state) {
            throw AuthFlowException("state mismatch: possible CSRF")
        }
        return uri.getQueryParameter("code")
            ?: throw AuthFlowException("authorization code not found in redirect")
    }

    private fun parseTokenErrorMessage(e: HttpException): String {
        val body = e.response()?.errorBody()?.string()
        return body?.let {
            runCatching {
                val jsonObject = json.parseToJsonElement(it).jsonObject
                (jsonObject["error_description"] ?: jsonObject["error"])?.jsonPrimitive?.content
            }.getOrNull()
        } ?: "token request failed (HTTP ${e.code()})"
    }

    private fun Credentials.toDomain() = AuthCredentials(
        accessToken = accessToken,
        idToken = idToken.ifEmpty { null },
        refreshToken = refreshToken,
        tokenType = type,
        expiresAt = expiresAt.time
    )

    private fun TokenResponse.toDomain() = AuthCredentials(
        accessToken = accessToken,
        idToken = idToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        expiresAt = System.currentTimeMillis() + expiresIn * 1000
    )
}
