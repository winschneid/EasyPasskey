package com.ks.app.easypasskey.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ks.app.easypasskey.domain.model.AuthCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.credentialsDataStore by preferencesDataStore(name = "auth_credentials")

/**
 * 認証情報の永続化。JSONシリアライズ → Keystore鍵でAES/GCM暗号化 → DataStoreに保存。
 */
@Singleton
class CredentialsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val json: Json
) {

    val credentials: Flow<AuthCredentials?> = context.credentialsDataStore.data
        .map { preferences ->
            preferences[credentialsKey]?.let { encoded ->
                // 復号失敗(鍵の消失・データ破損)は未ログイン扱いにする
                runCatching {
                    val blob = Base64.decode(encoded, Base64.NO_WRAP)
                    json.decodeFromString<AuthCredentials>(cryptoManager.decrypt(blob).decodeToString())
                }.getOrNull()
            }
        }

    suspend fun save(credentials: AuthCredentials) {
        val blob = cryptoManager.encrypt(json.encodeToString(credentials).encodeToByteArray())
        context.credentialsDataStore.edit { preferences ->
            preferences[credentialsKey] = Base64.encodeToString(blob, Base64.NO_WRAP)
        }
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { preferences ->
            preferences.remove(credentialsKey)
        }
    }

    private val credentialsKey = stringPreferencesKey("credentials")
}
