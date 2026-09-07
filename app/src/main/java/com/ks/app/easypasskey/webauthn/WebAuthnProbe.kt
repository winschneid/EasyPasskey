package com.ks.app.easypasskey.webauthn

import android.content.pm.ApplicationInfo
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * WebView に対する WebAuthn の許可レベル。
 *
 * - [NATIVE_FOR_APP] は WebView から Credential Manager を呼べるようにする。ただし呼び出し主体は
 *   「WebView を埋め込んでいるアプリ」で、Digital Asset Links による関連付けが前提になる。
 * - [DISABLED] は WebView の既定値。WebAuthn 呼び出しは失敗する。
 *
 * `WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER`（任意のサイトの代理で呼べる）は
 * 特権パーミッションが要るので、ここでは扱わない。
 */
enum class WebAuthnMode(val supportLevel: Int) {
    NATIVE_FOR_APP(WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP),
    DISABLED(WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE);

    companion object {
        fun of(nativeWebAuthn: Boolean) = if (nativeWebAuthn) NATIVE_FOR_APP else DISABLED
    }
}

const val PROBE_LOG_TAG = "PasskeyProbe"

/**
 * origin 実測用に自前で立てた最小 RP。
 *
 * Auth0 のテナント既定ドメインが返す assetlinks には `delegate_permission/common.get_login_creds`
 * が無いため、ceremony が Digital Asset Links の照合で落ちて clientDataJSON まで届かない。
 * こちらは `https://winschneid.github.io/.well-known/assetlinks.json` に release と debug
 * 両方の署名指紋を登録してあるので、debug ビルドのまま ceremony を通せる。
 */
const val PROBE_PAGE_URL = "https://winschneid.github.io/passkey-probe/"

/**
 * ネイティブ WebAuthn を [mode] に設定し、[PasskeyProbe] を差し込む。
 *
 * WebView を作った直後、`loadUrl` の前に呼ぶこと。
 * 観測した内容は Logcat（タグ [PROBE_LOG_TAG]）と [onLog] の両方に出す。
 * [onLog] は画面に出す用の短い 1 行で、Logcat には生の JSON をそのまま流す。
 */
fun WebView.installPasskeyProbe(mode: WebAuthnMode, onLog: (String) -> Unit) {
    fun log(line: String) {
        Log.i(PROBE_LOG_TAG, line)
        onLog(line)
    }

    if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        // chrome://inspect から中を見られるようにしておく
        WebView.setWebContentsDebuggingEnabled(true)
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
        WebSettingsCompat.setWebAuthenticationSupport(settings, mode.supportLevel)
        log("WEB_AUTHENTICATION = ${mode.name}")
    } else {
        log("WEB_AUTHENTICATION is NOT supported by this WebView")
    }
    WebViewCompat.getCurrentWebViewPackage(context)?.let {
        log("webview = ${it.packageName} ${it.versionName}")
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(
            this,
            PasskeyProbe.JS_OBJECT_NAME,
            ALLOWED_ORIGIN_RULES,
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            val raw = message.data ?: return@addWebMessageListener
            raw.chunked(LOGCAT_CHUNK).forEach {
                Log.i(PROBE_LOG_TAG, "$sourceOrigin (mainFrame=$isMainFrame) $it")
            }
            summarize(raw).forEach(onLog)
        }
    } else {
        log("WEB_MESSAGE_LISTENER is NOT supported: probe disabled")
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(this, PasskeyProbe.SCRIPT, ALLOWED_ORIGIN_RULES)
    } else {
        log("DOCUMENT_START_SCRIPT is NOT supported: probe disabled")
    }
}

/** probe から届いた JSON を、画面に出せる長さの行に落とす。 */
private fun summarize(raw: String): List<String> {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return listOf("probe (unparsed): $raw")
    return when (val event = json.optString("event")) {
        "webauthn.create.success", "webauthn.get.success" -> {
            val response = json.optJSONObject("response")
            listOf(
                event,
                "  page origin = ${json.optString("origin")}",
                // 本題。ここが android:apk-key-hash: のままかどうか。
                "  clientData origin = ${response?.optString("clientDataOrigin")}",
                "  androidPackageName = ${response?.opt("androidPackageName")}",
                "  attachment = ${response?.opt("authenticatorAttachment")}",
            )
        }

        "webauthn.create.failure", "webauthn.get.failure" ->
            listOf("$event: ${json.optString("errorName")} / ${json.optString("errorMessage")}")

        "webauthn.create.start", "webauthn.get.start" ->
            listOf("$event: rpId = ${json.optJSONObject("request")?.opt("rpId")}")

        "probe.installed" ->
            listOf("$event: PublicKeyCredential = ${json.opt("hasPublicKeyCredential")}")

        else -> listOf("$event: ${json.opt("available") ?: json.optString("origin")}")
    }
}

/**
 * 検証用なのであえて全 origin を対象にしている。ceremony が想定外の origin で走っても取りこぼさないため。
 * 製品コードにこの形で持ち込まないこと。
 */
private val ALLOWED_ORIGIN_RULES = setOf("*")

/** Logcat は 1 行が長すぎると切られるので分割して出す。 */
private const val LOGCAT_CHUNK = 3000
