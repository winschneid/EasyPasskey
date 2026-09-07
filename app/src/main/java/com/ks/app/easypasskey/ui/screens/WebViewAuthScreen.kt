package com.ks.app.easypasskey.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ks.app.easypasskey.auth.nosdk.WebViewAuthState
import com.ks.app.easypasskey.auth.nosdk.WebViewAuthViewModel
import com.ks.app.easypasskey.domain.model.AuthCredentials
import com.ks.app.easypasskey.webauthn.WebAuthnMode
import com.ks.app.easypasskey.webauthn.installPasskeyProbe
import java.text.DateFormat
import java.util.Date

/** probe のログを画面に残す行数。 */
private const val MAX_PROBE_LOG_LINES = 60

/**
 * probe のログ。WebView がアンマウントされた後（トークン交換中・成功後）も残したいので、
 * WebView ではなく画面全体のスコープで持つ。
 */
@Stable
private class ProbeLog {
    val lines = mutableStateListOf<String>()

    fun append(line: String) {
        lines.add(line)
        while (lines.size > MAX_PROBE_LOG_LINES) lines.removeAt(0)
    }
}

@Composable
fun WebViewAuthScreen(
    viewModel: WebViewAuthViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    webAuthnMode: WebAuthnMode = WebAuthnMode.NATIVE_FOR_APP
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val probeLog = remember { ProbeLog() }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (val current = state) {
                is WebViewAuthState.Authorizing -> AuthWebView(
                    authorizeUrl = current.authorizeUrl,
                    onNavigation = viewModel::onNavigation,
                    onClose = onClose,
                    webAuthnMode = webAuthnMode,
                    onProbeLog = probeLog::append
                )

                is WebViewAuthState.ExchangingToken -> LoadingContent("Exchanging token…")

                is WebViewAuthState.Success -> SuccessContent(
                    credentials = current.credentials,
                    onClose = onClose
                )

                is WebViewAuthState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = viewModel::restart,
                    onClose = onClose
                )
            }
        }

        ProbeLogPanel(lines = probeLog.lines)
    }
}

/**
 * Auth0 を経由せず、任意の URL を probe 付きで開くだけの画面。
 *
 * Auth0 のテナント既定ドメインが返す assetlinks には `get_login_creds` が無く、
 * ceremony が Digital Asset Links の照合で落ちて clientDataJSON まで届かない。
 * origin を実測するには、自前で assetlinks を置いた RP を開く必要がある。
 */
@Composable
fun ProbeWebViewScreen(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    webAuthnMode: WebAuthnMode = WebAuthnMode.NATIVE_FOR_APP
) {
    val probeLog = remember { ProbeLog() }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AuthWebView(
                authorizeUrl = url,
                // リダイレクトを横取りする相手がいないので、全部 WebView に任せる。
                onNavigation = { false },
                onClose = onClose,
                webAuthnMode = webAuthnMode,
                onProbeLog = probeLog::append
            )
        }

        ProbeLogPanel(lines = probeLog.lines)
    }
}

/** 観測結果をそのまま撮れるように画面下部に出す。Logcat にも同じ内容が出ている。 */
@Composable
private fun ProbeLogPanel(lines: List<String>) {
    val scrollState = rememberScrollState()
    // 行が増えて maxValue が動いた後に追従させる。lines.size をキーにすると
    // レイアウト前の古い maxValue まで飛んでしまう。
    LaunchedEffect(scrollState.maxValue) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuthWebView(
    authorizeUrl: String,
    onNavigation: (String) -> Boolean,
    onClose: () -> Unit,
    webAuthnMode: WebAuthnMode,
    onProbeLog: (String) -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler {
        val view = webView
        if (view != null && canGoBack) view.goBack() else onClose()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // Universal Login はJSとセッションストレージに依存する
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)

                // loadUrl の前に差し込む。ページ側のスクリプトより先に
                // navigator.credentials.create を掴む必要がある。
                // ログの反映は post で遅らせる。factory は composition 中に走るので、
                // ここで直接 state を書き換えると同じ composition の読み取りと衝突する。
                installPasskeyProbe(webAuthnMode) { line -> post { onProbeLog(line) } }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        return onNavigation(request.url.toString())
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        url: String?,
                        isReload: Boolean
                    ) {
                        canGoBack = view.canGoBack()
                    }
                }
                loadUrl(authorizeUrl)
                webView = this
            }
        },
        onRelease = { view ->
            webView = null
            view.destroy()
        }
    )
}

@Composable
private fun LoadingContent(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SuccessContent(
    credentials: AuthCredentials,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Authenticated (no SDK)",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "token_type: ${credentials.tokenType}\n" +
                    "expires_at: ${DateFormat.getDateTimeInstance().format(Date(credentials.expiresAt))}\n" +
                    "refresh_token: ${if (credentials.refreshToken != null) "issued" else "none"}\n" +
                    "id_token: ${if (credentials.idToken != null) "issued" else "none"}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Authentication failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Retry")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}
