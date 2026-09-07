package com.ks.app.easypasskey

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ks.app.easypasskey.auth.nosdk.WebViewAuthViewModel
import com.ks.app.easypasskey.ui.screens.ProbeWebViewScreen
import com.ks.app.easypasskey.ui.screens.WebViewAuthScreen
import com.ks.app.easypasskey.ui.theme.EasyPasskeyTheme
import com.ks.app.easypasskey.webauthn.PROBE_PAGE_URL
import com.ks.app.easypasskey.webauthn.WebAuthnMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * Auth0 SDK を使わない WebView ベースの認証画面。
 * 既存の MainActivity(SDK 版)とは独立した実験用の別画面。
 */
@AndroidEntryPoint
class WebViewAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyPasskeyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val webAuthnMode = WebAuthnMode.of(
                        intent.getBooleanExtra(EXTRA_NATIVE_WEBAUTHN, true)
                    )
                    val probeUrl = intent.getStringExtra(EXTRA_PROBE_URL)

                    if (probeUrl != null) {
                        // Auth0 を経由しない origin 実測モード。ViewModel は要らない。
                        ProbeWebViewScreen(
                            url = probeUrl,
                            onClose = { finish() },
                            modifier = Modifier.padding(innerPadding),
                            webAuthnMode = webAuthnMode
                        )
                    } else {
                        val authViewModel: WebViewAuthViewModel = viewModel()
                        WebViewAuthScreen(
                            viewModel = authViewModel,
                            onClose = { finish() },
                            modifier = Modifier.padding(innerPadding),
                            webAuthnMode = webAuthnMode
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_IS_SIGNUP = "extra_is_signup"

        /** WebView のネイティブ WebAuthn 対応を有効にするか。origin の比較用に切り替える。 */
        const val EXTRA_NATIVE_WEBAUTHN = "extra_native_webauthn"

        /** 指定すると Auth0 の認可フローではなく、この URL を probe 付きで開くだけになる。 */
        const val EXTRA_PROBE_URL = "extra_probe_url"

        fun createIntent(
            context: Context,
            isSignup: Boolean,
            nativeWebAuthn: Boolean = true
        ): Intent =
            Intent(context, WebViewAuthActivity::class.java)
                .putExtra(EXTRA_IS_SIGNUP, isSignup)
                .putExtra(EXTRA_NATIVE_WEBAUTHN, nativeWebAuthn)

        /** origin 実測用。[url] を WebView で開いて clientDataJSON を観測するだけ。 */
        fun createProbeIntent(
            context: Context,
            url: String = PROBE_PAGE_URL,
            nativeWebAuthn: Boolean = true
        ): Intent =
            Intent(context, WebViewAuthActivity::class.java)
                .putExtra(EXTRA_PROBE_URL, url)
                .putExtra(EXTRA_NATIVE_WEBAUTHN, nativeWebAuthn)
    }
}
