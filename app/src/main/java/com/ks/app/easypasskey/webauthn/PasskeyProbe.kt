package com.ks.app.easypasskey.webauthn

/**
 * WebView 内の WebAuthn 呼び出しを覗くための JavaScript。
 *
 * 知りたいのは 1 点だけ。AndroidX WebKit のネイティブ WebAuthn 対応を有効にしたとき、
 * `clientDataJSON` の `origin` が `android:apk-key-hash:` のままなのか、それとも `https://` になるのか。
 *
 * `navigator.credentials.create` / `.get` を差し替えて、リクエストとレスポンスの両方を Kotlin 側へ送る。
 * サーバが資格情報を受理する必要はない。ブラウザは JS に資格情報を返した後でページがサーバへ送るので、
 * Auth0 に弾かれる前に origin を読める。
 *
 * `WebViewCompat.addDocumentStartJavaScript` で差し込むこと。`onPageFinished` では
 * ページ側のスクリプトが先に `navigator.credentials.create` を掴んでしまい、ラップが効かない。
 */
internal object PasskeyProbe {

    /** JS 側から結果を返すために公開する `WebMessageListener` の名前。 */
    const val JS_OBJECT_NAME = "AndroidProbe"

    val SCRIPT = """
(function () {
  if (window.__ezpkProbeInstalled) { return; }
  window.__ezpkProbeInstalled = true;

  var send = function (event, data) {
    var payload = { event: event, href: location.href, origin: location.origin };
    for (var key in data) { payload[key] = data[key]; }
    try {
      $JS_OBJECT_NAME.postMessage(JSON.stringify(payload));
    } catch (e) {
      // このフレームにはリスナーが付いていない
    }
  };

  var text = function (buffer) {
    try { return new TextDecoder().decode(buffer); }
    catch (e) { return '<decode failed: ' + e + '>'; }
  };

  var describeRequest = function (options) {
    var pk = options && options.publicKey;
    if (!pk) { return null; }
    var selection = pk.authenticatorSelection || {};
    return {
      rpId: pk.rp ? pk.rp.id : (pk.rpId || null),
      rpName: pk.rp ? pk.rp.name : null,
      userVerification: selection.userVerification || pk.userVerification || null,
      residentKey: selection.residentKey || null,
      authenticatorAttachment: selection.authenticatorAttachment || null,
      mediation: options.mediation || null
    };
  };

  var describeResponse = function (credential) {
    if (!credential) { return null; }
    var response = credential.response || {};
    var out = {
      credentialId: credential.id || null,
      credentialType: credential.type || null,
      authenticatorAttachment: credential.authenticatorAttachment || null,
      clientDataJSON: response.clientDataJSON ? text(response.clientDataJSON) : null
    };
    if (out.clientDataJSON) {
      try {
        var parsed = JSON.parse(out.clientDataJSON);
        // ここが本題。android:apk-key-hash: か https:// か。
        out.clientDataOrigin = parsed.origin;
        out.clientDataType = parsed.type;
        out.crossOrigin = parsed.crossOrigin;
        out.androidPackageName = parsed.androidPackageName || null;
      } catch (e) {
        out.clientDataParseError = String(e);
      }
    }
    return out;
  };

  var wrap = function (name) {
    var credentials = navigator.credentials;
    if (!credentials || typeof credentials[name] !== 'function') {
      send('probe.skip', { reason: 'navigator.credentials.' + name + ' is unavailable' });
      return;
    }
    var original = credentials[name].bind(credentials);
    credentials[name] = function (options) {
      send('webauthn.' + name + '.start', { request: describeRequest(options) });
      var pending;
      try {
        pending = original(options);
      } catch (e) {
        send('webauthn.' + name + '.failure', {
          errorName: e && e.name, errorMessage: e && e.message, error: String(e), synchronous: true
        });
        throw e;
      }
      return Promise.resolve(pending).then(function (credential) {
        send('webauthn.' + name + '.success', { response: describeResponse(credential) });
        return credential;
      }, function (e) {
        send('webauthn.' + name + '.failure', {
          errorName: e && e.name, errorMessage: e && e.message, error: String(e)
        });
        throw e;
      });
    };
  };

  wrap('create');
  wrap('get');

  send('probe.installed', {
    secureContext: window.isSecureContext,
    hasPublicKeyCredential: typeof window.PublicKeyCredential !== 'undefined',
    userAgent: navigator.userAgent
  });

  if (typeof window.PublicKeyCredential !== 'undefined') {
    if (PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable) {
      PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable().then(
        function (v) { send('probe.uvpaa', { available: v }); },
        function (e) { send('probe.uvpaa', { error: String(e) }); }
      );
    }
    if (PublicKeyCredential.isConditionalMediationAvailable) {
      PublicKeyCredential.isConditionalMediationAvailable().then(
        function (v) { send('probe.conditionalMediation', { available: v }); },
        function (e) { send('probe.conditionalMediation', { error: String(e) }); }
      );
    }
  }
})();
""".trimIndent()
}
