package com.example.pantrypal

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pantrypal.data.repository.AuthRepository
import kotlinx.coroutines.launch

class CreateAccountActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val fullName = findViewById<EditText>(R.id.etFullName)
        val email = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val termsCheckBox = findViewById<CheckBox>(R.id.cbTerms)
        val createAccountButton = findViewById<Button>(R.id.btnCreateAccount)
        val txtSignIn = findViewById<TextView>(R.id.txtSignIn)

        btnBack?.setOnClickListener {
            finish()
        }

        txtSignIn?.setOnClickListener {
            finish()
        }

        createAccountButton.setOnClickListener {
            val nameText = fullName.text.toString().trim()
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString()
            val confirmPasswordText = confirmPassword.text.toString()

            // 1. Full Name empty
            if (nameText.isEmpty()) {
                fullName.error = "Please enter your full name"
                fullName.requestFocus()
                return@setOnClickListener
            }

            // 2. First letter must be uppercase
            if (!nameText[0].isUpperCase()) {
                fullName.error = "First letter must be capitalized"
                fullName.requestFocus()
                return@setOnClickListener
            }

            // 3. Email empty
            if (emailText.isEmpty()) {
                email.error = "Please enter your email"
                email.requestFocus()
                return@setOnClickListener
            }

            // 4. Valid email
            if (!Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
                email.error = "Please enter a valid email address"
                email.requestFocus()
                return@setOnClickListener
            }

            // 5. Password empty
            if (passwordText.isEmpty()) {
                password.error = "Please enter a password"
                password.requestFocus()
                return@setOnClickListener
            }

            // 6. Password minimum 5 characters
            if (passwordText.length < 5) {
                password.error = "Password must contain at least 5 characters"
                password.requestFocus()
                return@setOnClickListener
            }

            // 7. Password must contain at least one letter
            if (!passwordText.any { it.isLetter() }) {
                password.error = "Password must contain at least one letter"
                password.requestFocus()
                return@setOnClickListener
            }

            // 8. Confirm password empty
            if (confirmPasswordText.isEmpty()) {
                confirmPassword.error = "Please confirm your password"
                confirmPassword.requestFocus()
                return@setOnClickListener
            }

            // 9. Passwords must match
            if (passwordText != confirmPasswordText) {
                confirmPassword.error = "Passwords do not match"
                confirmPassword.requestFocus()
                return@setOnClickListener
            }

            // 10. Terms & Conditions
            if (!termsCheckBox.isChecked) {
                Toast.makeText(
                    this,
                    "Please agree to the Terms & Conditions",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Perform Account Creation with Supabase DB & Auth
            createAccountButton.isEnabled = false
            createAccountButton.text = "Creating Account..."

            lifecycleScope.launch {
                val result = authRepository.signUp(emailText, nameText, passwordText)

                createAccountButton.isEnabled = true
                createAccountButton.text = "Create Account"

                result.onSuccess {
                    val prefs = getSharedPreferences("pantry_pal_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("user_email", emailText)
                        .putString("user_name", nameText)
                        .putBoolean("is_logged_in", true)
                        .apply()

                    Toast.makeText(
                        this@CreateAccountActivity,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(this@CreateAccountActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }.onFailure { ex ->
                    Toast.makeText(
                        this@CreateAccountActivity,
                        "Failed to create account: ${ex.localizedMessage ?: "Check network connection"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
