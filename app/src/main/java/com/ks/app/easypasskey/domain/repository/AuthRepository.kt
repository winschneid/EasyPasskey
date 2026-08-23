package com.ks.app.easypasskey.domain.repository

import android.content.Context
import com.ks.app.easypasskey.domain.model.AuthCredentials
import com.ks.app.easypasskey.domain.model.WebAuthorization
import kotlinx.coroutines.flow.Flow

/**
 * 認証のデータ層への窓口。
 * SDK版(Custom Tabs)とSDKなし版(WebView)の2つのフローを提供し、
 * どちらで取得した認証情報も同じストアに永続化される。
 *
 * 注: loginWithBrowser の Context は Auth0 SDK が Custom Tabs 起動に
 * Activity コンテキストを要求するため。保持はしない。
 */
interface AuthRepository {

    /** 保存済みの認証情報。未ログインなら null を流す。 */
    val credentials: Flow<AuthCredentials?>

    /** Auth0 SDK(Custom Tabs)によるユニバーサルログイン。成功時は永続化される。 */
    suspend fun loginWithBrowser(context: Context, isSignup: Boolean): AuthCredentials

    /** SDKなしWebViewフロー: PKCE付きの認可リクエストを生成する。 */
    fun createWebAuthorization(isSignup: Boolean): WebAuthorization

    /**
     * SDKなしWebViewフロー: リダイレクトURLを検証し、認可コードをトークンに交換する。
     * 成功時は永続化される。
     */
    suspend fun completeWebAuthorization(
        authorization: WebAuthorization,
        redirectUrl: String
    ): AuthCredentials

    /** 保存済み認証情報を破棄する。 */
    suspend fun logout()
}
