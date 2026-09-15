package com.ks.app.easypasskey.domain.model

/**
 * SDKなしWebViewフローの1回分の認可リクエスト。
 * PKCE verifier と state はこのリクエストに紐づき、トークン交換時の検証に使う。
 */
data class WebAuthorization(
    val authorizeUrl: String,
    val redirectUri: String,
    val codeVerifier: String,
    val state: String
) {
    fun isRedirect(url: String): Boolean = url.startsWith(redirectUri)
}
