package com.ks.app.easypasskey.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ks.app.easypasskey.domain.model.AuthCredentials
import com.ks.app.easypasskey.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val credentials: AuthCredentials? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.credentials.collect { credentials ->
                _uiState.update {
                    it.copy(isAuthenticated = credentials != null, credentials = credentials)
                }
            }
        }
    }

    fun signup(context: Context) = authenticate(context, isSignup = true)

    fun login(context: Context) = authenticate(context, isSignup = false)

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun authenticate(context: Context, isSignup: Boolean) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // 成功時の isAuthenticated 反映は credentials Flow 経由で行われる
                authRepository.loginWithBrowser(context, isSignup)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "authentication failed")
                }
            }
        }
    }
}
