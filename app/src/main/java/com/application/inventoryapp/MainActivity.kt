package com.application.inventoryapp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.widget.Button
import android.widget.Toast
import com.google.firebase.FirebaseApp

// MainActivity inherits from AppCompatActivity to get all the core Android activity features
class MainActivity : AppCompatActivity() {

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Call parent class's onCreate to setup system stuff
        FirebaseApp.initializeApp(this) // Initialize Firebase for this app.
        enableEdgeToEdge() // Custom function to make app draw behind system bars (fullscreen look)

        setContentView(R.layout.activity_main) // Set the UI layout for this activity from XML file

        val scanButton = findViewById<Button>(R.id.button)
        scanButton.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            startActivityForResult(intent, 1002)
        }

        // Find the root view by ID 'main' and set a listener for window insets (status/nav bars)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->

            // Get the size of system bars (status bar, navigation bar) in pixels
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Add padding to the view so UI elements don't get hidden behind system bars
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            insets // Return the insets unchanged


        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1002 && resultCode == RESULT_OK) {
            val barcode = data?.getStringExtra("barcode")
            Toast.makeText(this, "Scanned: $barcode", Toast.LENGTH_LONG).show()
            // Update UI or whatever you wanna do with it
        }
    }

}
