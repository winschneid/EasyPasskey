# Auth0 SDK を使う実装と使わない実装の比較

このリポジトリには Auth0 の認証(サインアップ・ログイン)について 2 系統の実装がある。

| 実装 | 方式 | エントリポイント |
|---|---|---|
| SDK 版 | Auth0 Android SDK(`WebAuthProvider`、内部は Custom Tabs) | `MainActivity` の Sign Up / Login ボタン |
| SDK なし版 | WebView + 手動 OAuth 2.0 認可コードフロー + PKCE | `WebViewAuthActivity`(Sign Up (WebView) / Login (WebView) ボタン) |

どちらも同じ `AuthRepository` を経由し、取得した認証情報は同じ暗号化ストア
(Keystore AES/GCM + DataStore)に保存される。

---

## Auth0 SDK(`WebAuthProvider`)を使うメリット

- **OAuth 2.0 / OIDC の実装が不要**
  PKCE の生成、state 検証、認可コードのトークン交換、ID トークンの署名検証などを
  SDK が正しく実装済み。自前実装で起きがちなセキュリティ上のミスを避けられる。
- **Custom Tabs ベースでベストプラクティス準拠**
  ネイティブアプリの OAuth は「外部ユーザーエージェント(ブラウザ)を使う」ことが
  RFC 8252 (OAuth 2.0 for Native Apps) で推奨されており、SDK はこれに従っている。
- **パスキー(WebAuthn)がそのまま動く**
  Custom Tabs は実体がブラウザなので WebAuthn API が使え、Auth0 Universal Login の
  パスキー認証が追加実装なしで動作する。このアプリ(EasyPasskey)の趣旨的に重要。
- **ソーシャルログインが動く**
  Google は埋め込み WebView からの OAuth を `disallowed_useragent` エラーで拒否するが、
  Custom Tabs なら問題ない。
- **ブラウザとの SSO セッション共有**
  ブラウザの Cookie を共有するため、シングルサインオン(既にログイン済みなら
  パスワード入力を省略)が効く。
- **付帯機能が揃っている**
  `CredentialsManager`(トークンの安全な保存と自動リフレッシュ)、ログアウト、
  Management API 呼び出しなどが提供される。
- **保守コストが低い**
  Auth0 側の仕様変更・セキュリティ修正に SDK のバージョンアップで追従できる。

## Auth0 SDK を使うデメリット

- **依存が増える**(`com.auth0.android:auth0` とその推移的依存)。
- **UI のカスタマイズ余地が小さい**
  一瞬ブラウザ(Custom Tabs)に遷移する UX になり、画面内に認証 UI を
  埋め込むことはできない。見た目のカスタマイズは Universal Login 側の設定に依存する。
- **内部動作がブラックボックスになりやすい**
  コールバック URL のスキーム設定や `AuthenticationActivity` の manifest 登録など、
  SDK の規約に従う必要があり、挙動の詳細を把握しにくい。
- **コールバック API が古い**
  `Callback<Credentials, AuthenticationException>` ベースで、コルーチンに
  自前でブリッジする必要がある(本リポジトリでは `Auth0WebAuthDataSource` で吸収)。

---

## SDK なしで実装する場合の選択肢

### 選択肢 1: Custom Tabs を自前で開く(本番向けの推奨)

SDK が内部でやっていることの手動版。認可 URL を Custom Tabs で開き、
コールバック URL を intent-filter(App Links / カスタムスキーム)で受け取り、
自前でトークン交換する。

- 長所: SDK 版と同じセキュリティ特性(WebAuthn・ソーシャルログイン・SSO が動く)を
  依存なしで得られる。
- 短所: intent-filter の設定、Activity の launchMode 調整、ブラウザ未インストール時の
  フォールバックなど、周辺の作り込みが必要。

### 選択肢 2: WebView(本リポジトリの実験実装)

認可ページを `WebView` に表示し、`shouldOverrideUrlLoading` でリダイレクトを
インターセプトして認可コードを取得、`/oauth/token` へ直接 POST する。

#### WebView 方式のメリット

- **自己完結する**
  リダイレクトを WebView 内で直接捕捉できるため、intent-filter も
  外部ブラウザへの遷移も不要。実験・学習用途では最も見通しが良い。
- **フローを完全に制御・観察できる**
  認可リクエスト、リダイレクト、トークン交換のすべてが自前コードなので、
  OAuth 2.0 + PKCE の学習・デバッグに向く。
- **画面遷移がアプリ内で完結する**(ブラウザに飛ばない UX)。

#### WebView 方式のデメリット

- **パスキー(WebAuthn)が動かない**
  WebView は標準では WebAuthn API を提供しない。Credential Manager への
  ブリッジを自前実装しない限り、パスキー認証は不可。
  **このアプリの名前的に、将来パスキーを使うなら致命的。**
- **Google ソーシャルログインがブロックされる**
  埋め込み WebView からの OAuth は Google が `disallowed_useragent` で拒否する
  (メール + パスワードの DB コネクションは動く)。
- **SSO セッションを共有できない**
  WebView の Cookie はブラウザと隔離されているため、シングルサインオンが効かない。
- **セキュリティモデル上の問題**
  ホストアプリがログインページの入力内容(パスワード等)を覗ける構造になるため、
  RFC 8252 で非推奨とされている。IdP によっては利用規約違反になる場合もある。
- **自前実装の責任範囲が広い**
  PKCE、state 検証、エラー処理、トークン保存をすべて正しく書く必要がある。

---

## 比較まとめ

| 観点 | SDK(Custom Tabs) | 自前 Custom Tabs | WebView(自前) |
|---|---|---|---|
| 実装コスト | 低 | 中 | 中 |
| 追加依存 | あり | なし | なし |
| RFC 8252 準拠 | ○ | ○ | ×(非推奨方式) |
| パスキー(WebAuthn) | ○ | ○ | ×(要ブリッジ実装) |
| Google ソーシャルログイン | ○ | ○ | × |
| ブラウザとの SSO | ○ | ○ | × |
| アプリ内完結の UX | ×(ブラウザ遷移) | ×(ブラウザ遷移) | ○ |
| フローの学習・観察 | △(ブラックボックス) | ○ | ◎ |
| 保守性 | ◎(SDK 更新に追従) | △ | △ |

## 結論

- **本番アプリ**: Auth0 SDK(または自前 Custom Tabs)を使う。
  特にパスキーを使う予定があるなら WebView は選択肢にならない。
- **学習・実験**: WebView 方式は OAuth 2.0 + PKCE のフローを理解するのに適しており、
  本リポジトリでは既存 SDK 実装と独立した `WebViewAuthActivity` として実装している。

## 参考

- [RFC 8252: OAuth 2.0 for Native Apps](https://datatracker.ietf.org/doc/html/rfc8252)
- [RFC 7636: Proof Key for Code Exchange (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636)
- [Auth0 Android SDK](https://github.com/auth0/Auth0.Android)
- [Google: disallowed_useragent(埋め込み WebView での OAuth 禁止)](https://developers.google.com/identity/protocols/oauth2/policies#browsers)
