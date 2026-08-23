package com.ks.app.easypasskey.data.sdk

import android.content.Context
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.ks.app.easypasskey.data.Auth0Config
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Auth0 SDK (Custom Tabs) のコールバックAPIを suspend 関数に変換するデータソース */
@Singleton
class Auth0WebAuthDataSource @Inject constructor(
    private val config: Auth0Config
) {

    suspend fun login(context: Context, isSignup: Boolean): Credentials =
        suspendCancellableCoroutine { continuation ->
            val scope = if (isSignup) {
                "openid profile email"
            } else {
                "openid profile email offline_access"
            }
            WebAuthProvider
                .login(Auth0.getInstance(config.clientId, config.domain))
                .withScheme(config.scheme)
                .withScope(scope)
                .withParameters(buildMap {
                    put("prompt", "login")
                    if (isSignup) put("screen_hint", "signup")
                })
                .start(context, object : Callback<Credentials, AuthenticationException> {
                    override fun onSuccess(result: Credentials) {
                        continuation.resume(result)
                    }

                    override fun onFailure(error: AuthenticationException) {
                        continuation.resumeWithException(error)
                    }
                })
        }
}
