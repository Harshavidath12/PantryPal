package com.example.pantrypal

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pantrypal.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val pbLoading = findViewById<ProgressBar>(R.id.pbLoading)
        val tvToggleMode = findViewById<TextView>(R.id.tvToggleMode)
        val btnBack = findViewById<TextView>(R.id.btnBack)

        btnBack?.setOnClickListener {
            finish()
        }

        // Redirect to Create Account screen
        tvToggleMode?.setOnClickListener {
            val intent = Intent(this, CreateAccountActivity::class.java)
            startActivity(intent)
        }

        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Input Validation
            var isValid = true

            if (email.isEmpty()) {
                etEmail.error = "Please enter your email address"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Please enter a valid email address"
                isValid = false
            } else {
                etEmail.error = null
            }

            if (password.isEmpty()) {
                etPassword.error = "Please enter your password"
                isValid = false
            } else if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                isValid = false
            } else {
                etPassword.error = null
            }

            if (!isValid) return@setOnClickListener

            // Perform Auth request
            btnSignIn.text = ""
            btnSignIn.isEnabled = false
            pbLoading?.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = authRepository.signIn(email, password)

                pbLoading?.visibility = View.GONE
                btnSignIn.isEnabled = true
                btnSignIn.text = "Sign In"

                result.onSuccess { user ->
                    val prefs = getSharedPreferences("pantry_pal_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("user_email", user.email)
                        .putString("user_name", user.name)
                        .putBoolean("is_logged_in", true)
                        .apply()

                    Toast.makeText(this@LoginActivity, "Welcome back, ${user.name}!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }.onFailure { ex ->
                    Toast.makeText(
                        this@LoginActivity,
                        "Authentication error: ${ex.localizedMessage ?: "Check network connection"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
