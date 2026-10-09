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
        val edtFullName = findViewById<EditText>(R.id.edtFullName)
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val edtConfirmPassword = findViewById<EditText>(R.id.edtConfirmPassword)
        val chkTerms = findViewById<CheckBox>(R.id.chkTerms)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)
        val txtSignIn = findViewById<TextView>(R.id.txtSignIn)

        btnBack?.setOnClickListener {
            finish()
        }

        txtSignIn?.setOnClickListener {
            finish()
        }

        btnCreateAccount.setOnClickListener {
            val fullName = edtFullName.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()
            val confirmPassword = edtConfirmPassword.text.toString().trim()

            // Validations
            var isValid = true

            if (fullName.isEmpty()) {
                edtFullName.error = "Please enter your full name"
                isValid = false
            } else {
                edtFullName.error = null
            }

            if (email.isEmpty()) {
                edtEmail.error = "Please enter your email address"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edtEmail.error = "Please enter a valid email address"
                isValid = false
            } else {
                edtEmail.error = null
            }

            if (password.isEmpty()) {
                edtPassword.error = "Please enter a password"
                isValid = false
            } else if (password.length < 6) {
                edtPassword.error = "Password must be at least 6 characters"
                isValid = false
            } else {
                edtPassword.error = null
            }

            if (confirmPassword.isEmpty()) {
                edtConfirmPassword.error = "Please confirm your password"
                isValid = false
            } else if (confirmPassword != password) {
                edtConfirmPassword.error = "Passwords do not match"
                isValid = false
            } else {
                edtConfirmPassword.error = null
            }

            if (!chkTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms & Conditions", Toast.LENGTH_SHORT).show()
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            // Perform Account Creation
            btnCreateAccount.isEnabled = false
            btnCreateAccount.text = "Creating Account..."

            lifecycleScope.launch {
                val result = authRepository.signUp(email, fullName, password)

                btnCreateAccount.isEnabled = true
                btnCreateAccount.text = "Create Account"

                result.onSuccess {
                    val prefs = getSharedPreferences("pantry_pal_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("user_email", email)
                        .putString("user_name", fullName)
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
