package com.ks.app.easypasskey.data

/** Auth0テナントの設定値。リソースから読み込み、DIで各データソースに配布される。 */
data class Auth0Config(
    val domain: String,
    val clientId: String,
    val scheme: String,
    val packageName: String
) {
    /** Auth0 SDK のデフォルトと同じ形式のコールバックURL(ダッシュボード設定を共用) */
    val redirectUri: String = "$scheme://$domain/android/$packageName/callback"
}
