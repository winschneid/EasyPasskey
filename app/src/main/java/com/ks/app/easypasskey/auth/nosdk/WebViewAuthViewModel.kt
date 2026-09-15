package com.ks.app.easypasskey.auth.nosdk

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ks.app.easypasskey.WebViewAuthActivity
import com.ks.app.easypasskey.domain.model.AuthCredentials
import com.ks.app.easypasskey.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WebViewAuthState {
    /** WebView で認可ページを表示中 */
    data class Authorizing(val authorizeUrl: String) : WebViewAuthState

    /** 認可コードをトークンに交換中 */
    data object ExchangingToken : WebViewAuthState

    data class Success(val credentials: AuthCredentials) : WebViewAuthState

    data class Error(val message: String) : WebViewAuthState
}

@HiltViewModel
class WebViewAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Intent extra は SavedStateHandle 経由で受け取る(プロセス再生成にも耐える)
    private val isSignup: Boolean =
        savedStateHandle[WebViewAuthActivity.EXTRA_IS_SIGNUP] ?: false

    private var authorization = authRepository.createWebAuthorization(isSignup)

    private val _state = MutableStateFlow<WebViewAuthState>(
        WebViewAuthState.Authorizing(authorization.authorizeUrl)
    )
    val state: StateFlow<WebViewAuthState> = _state.asStateFlow()

    fun restart() {
        authorization = authRepository.createWebAuthorization(isSignup)
        _state.value = WebViewAuthState.Authorizing(authorization.authorizeUrl)
    }

    /**
     * WebView がリダイレクトURLに到達したら true を返して読み込みを止め、
     * コードのトークン交換に進む。
     */
    fun onNavigation(url: String): Boolean {
        if (!authorization.isRedirect(url)) return false

        _state.value = WebViewAuthState.ExchangingToken
        viewModelScope.launch {
            try {
                val credentials = authRepository.completeWebAuthorization(authorization, url)
                _state.value = WebViewAuthState.Success(credentials)
            } catch (e: Exception) {
                _state.value = WebViewAuthState.Error(e.message ?: "authentication failed")
            }
        }
        return true
    }
}
