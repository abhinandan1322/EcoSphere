package com.example.capstone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone.databinding.ActivityContactBinding

class ContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Email card click
        binding.cardEmail.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:abhijha1322@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "EcoSphere Support")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "You dont have the required app", Toast.LENGTH_SHORT).show()
            }
        }

        // Phone card click
        binding.cardPhone.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:+91 9073578098")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No phone app found", Toast.LENGTH_SHORT).show()
            }
        }

        // Send message button
        binding.btnSendEmail.setOnClickListener {
            sendEmail()
        }
    }

    private fun sendEmail() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val subject = binding.etSubject.text.toString().trim()
        val message = binding.etMessage.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || subject.isEmpty() || message.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("abhijha1322@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "Name: $name\nEmail: $email\n\nMessage:\n$message")
            }
            
            startActivity(Intent.createChooser(emailIntent, "Send email via..."))
            Toast.makeText(this, "Opening email app...", Toast.LENGTH_SHORT).show()
            
            // Clear form after sending
            binding.etName.text?.clear()
            binding.etEmail.text?.clear()
            binding.etSubject.text?.clear()
            binding.etMessage.text?.clear()
        } catch (e: Exception) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}
