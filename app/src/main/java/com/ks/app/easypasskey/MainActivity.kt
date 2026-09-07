package com.ks.app.easypasskey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ks.app.easypasskey.auth.AuthViewModel
import com.ks.app.easypasskey.ui.screens.AuthScreen
import com.ks.app.easypasskey.ui.theme.EasyPasskeyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyPasskeyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val authViewModel: AuthViewModel = viewModel()

                    AuthScreen(
                        authViewModel = authViewModel,
                        onSignupClick = { authViewModel.signup(this) },
                        onLoginClick = { authViewModel.login(this) },
                        onWebViewSignupClick = { nativeWebAuthn ->
                            startActivity(
                                WebViewAuthActivity.createIntent(
                                    this,
                                    isSignup = true,
                                    nativeWebAuthn = nativeWebAuthn
                                )
                            )
                        },
                        onWebViewLoginClick = { nativeWebAuthn ->
                            startActivity(
                                WebViewAuthActivity.createIntent(
                                    this,
                                    isSignup = false,
                                    nativeWebAuthn = nativeWebAuthn
                                )
                            )
                        },
                        onProbePageClick = { nativeWebAuthn ->
                            startActivity(
                                WebViewAuthActivity.createProbeIntent(
                                    this,
                                    nativeWebAuthn = nativeWebAuthn
                                )
                            )
                        },
                        onLogoutClick = { authViewModel.logout() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
