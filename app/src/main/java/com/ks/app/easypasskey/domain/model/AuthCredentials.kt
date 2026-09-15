package com.ks.app.easypasskey.domain.model

import kotlinx.serialization.Serializable

/**
 * SDK版・WebView版の両フローで共通の認証情報モデル。
 * Auth0 SDK の Credentials / トークンAPIレスポンスの両方からマップされる。
 */
@Serializable
data class AuthCredentials(
    val accessToken: String,
    val idToken: String?,
    val refreshToken: String?,
    val tokenType: String,
    /** アクセストークンの失効時刻(エポックミリ秒) */
    val expiresAt: Long
)
