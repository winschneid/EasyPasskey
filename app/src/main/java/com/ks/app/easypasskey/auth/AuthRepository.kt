package com.ks.app.easypasskey.auth

import android.content.Context
import androidx.activity.ComponentActivity
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.ks.app.easypasskey.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


interface AuthRepository {
    suspend fun login(): Result<Credentials>
    suspend fun signup(): Result<Credentials>
    suspend fun logout(): Result<Unit>
}

class Auth0Repository(
    private val context: Context
) : AuthRepository {

    private val auth0Instance = Auth0.getInstance(context)

    private val authenticationAPIClient: AuthenticationAPIClient by lazy {
        AuthenticationAPIClient(auth0Instance)
    }

    private fun requireActivity(): ComponentActivity {
        return context as? ComponentActivity
            ?: throw IllegalStateException("Context must be ComponentActivity for Auth0 operations")
    }

    override suspend fun login(): Result<Credentials> {
        return try {
            val activity = requireActivity()
            val credentials = suspendCancellableCoroutine<Credentials> { continuation ->
                WebAuthProvider
                    .login(auth0Instance)
                    .withScheme(context.getString(R.string.com_auth0_scheme))
                    .withScope("openid profile email offline_access")
                    .withParameters(mapOf("prompt" to "login"))
                    .start(activity, object : Callback<Credentials, AuthenticationException> {
                        override fun onSuccess(result: Credentials) {
                            continuation.resume(result)
                        }

                        override fun onFailure(error: AuthenticationException) {
                            continuation.resumeWithException(error)
                        }
                    })
            }
            Result.success(credentials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signup(): Result<Credentials> {
        return try {
            val activity = requireActivity()
            val credentials = suspendCancellableCoroutine<Credentials> { continuation ->
                WebAuthProvider
                    .login(auth0Instance)
                    .withScheme(context.getString(R.string.com_auth0_scheme))
                    .withScope("openid profile email")
                    .withParameters(mapOf("prompt" to "login", "screen_hint" to "signup"))
                    .start(activity, object : Callback<Credentials, AuthenticationException> {
                        override fun onSuccess(result: Credentials) {
                            continuation.resume(result)
                        }

                        override fun onFailure(error: AuthenticationException) {
                            continuation.resumeWithException(error)
                        }
                    })
            }
            Result.success(credentials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val activity = requireActivity()
            suspendCancellableCoroutine<Unit> { continuation ->
                WebAuthProvider
                    .logout(auth0Instance)
                    .withScheme(context.getString(R.string.com_auth0_scheme))
                    .start(activity, object : Callback<Void?, AuthenticationException> {
                        override fun onSuccess(result: Void?) {
                            continuation.resume(Unit)
                        }

                        override fun onFailure(error: AuthenticationException) {
                            continuation.resumeWithException(error)
                        }
                    })
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}