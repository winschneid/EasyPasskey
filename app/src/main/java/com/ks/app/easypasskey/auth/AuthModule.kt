package com.ks.app.easypasskey.auth

import android.content.Context
import com.auth0.android.Auth0
import com.auth0.android.provider.WebAuthProvider
import com.ks.app.easypasskey.R


internal object AuthModule {

    fun signupAuthBuilder(context: Context): WebAuthProvider.Builder {
        return WebAuthProvider
            .login(Auth0.getInstance(context))
            .withScheme(context.getString(R.string.com_auth0_scheme))
            .withScope("openid profile email")
            .withParameters(mapOf("prompt" to "login", "screen_hint" to "signup"))
    }

    fun loginAuthBuilder(context: Context): WebAuthProvider.Builder {
        return WebAuthProvider
            .login(Auth0.getInstance(context))
            .withScheme(context.getString(R.string.com_auth0_scheme))
            .withScope("openid profile email offline_access")
            .withParameters(mapOf("prompt" to "login"))
    }
}