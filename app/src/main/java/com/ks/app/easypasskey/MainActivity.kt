package com.ks.app.easypasskey

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import com.ks.app.easypasskey.auth.AuthModule
import com.ks.app.easypasskey.auth.AuthViewModel
import com.ks.app.easypasskey.ui.screens.AuthScreen
import com.ks.app.easypasskey.ui.theme.EasyPasskeyTheme

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
                        onSignupClick = { signup() },
                        onLoginClick = { login() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    fun signup() {
        AuthModule.signupAuthBuilder(this)
            .start(this, object : Callback<Credentials, AuthenticationException> {
                override fun onSuccess(result: Credentials) {
                    // Handle success
                    Toast.makeText(this@MainActivity, "Signup successful", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(error: AuthenticationException) {
                    // Handle failure
                    Toast.makeText(this@MainActivity, "Signup failed", Toast.LENGTH_SHORT).show()
                }
            })
    }

    fun login() {
        AuthModule.loginAuthBuilder(this)
            .start(this, object : Callback<Credentials, AuthenticationException> {
                override fun onSuccess(result: Credentials) {
                    // Handle success
                    Toast.makeText(this@MainActivity, "Login successful", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(error: AuthenticationException) {
                    // Handle failure
                    Toast.makeText(this@MainActivity, "Login failed", Toast.LENGTH_SHORT).show()
                }
            })
    }
}