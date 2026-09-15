package com.ks.app.easypasskey.data.pkce

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** PKCE (RFC 7636) の code_verifier / code_challenge と state の生成 */
object Pkce {

    fun generateCodeVerifier(): String = generateRandomToken()

    fun generateState(): String = generateRandomToken()

    fun codeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, BASE64_FLAGS)
    }

    private fun generateRandomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, BASE64_FLAGS)
    }

    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
}
