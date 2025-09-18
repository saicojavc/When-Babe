package com.saico.whenbabe

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
// Import androidx.compose.material3.Text if you use it directly in MainScreen, otherwise it might be unused here.
import androidx.compose.ui.Modifier
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.saico.whenbabe.sceen.MainScreen
import com.saico.whenbabe.ui.theme.WhenBabeTheme
import java.util.UUID

private const val PREFS_NAME = "WhenBabePrefs"
private const val USER_ID_KEY = "user_id"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var userId = prefs.getString(USER_ID_KEY, null)

        if (userId == null) {
            userId = UUID.randomUUID().toString()
            with(prefs.edit()) {
                putString(USER_ID_KEY, userId)
                apply()
            }

            // Store in Firebase Realtime Database
            try {
                val database = Firebase.database
                // You can customize the path and structure as needed
                // For example, storing a simple timestamp or more user details
                database.getReference("users").child(userId).setValue(mapOf("registeredAt" to System.currentTimeMillis()))
                    .addOnSuccessListener {
                        // Optional: Log success or handle UI updates
                        android.util.Log.d("MainActivity", "User ID stored in Firebase: $userId")
                    }
                    .addOnFailureListener { e ->
                        // Optional: Log error or handle UI updates for failure
                        android.util.Log.e("MainActivity", "Failed to store User ID in Firebase", e)
                    }
            } catch (e: Exception) {
                // Catch any potential exceptions during Firebase initialization or access
                android.util.Log.e("MainActivity", "Error accessing Firebase Database", e)
            }
        } else {
            android.util.Log.d("MainActivity", "Existing User ID found: $userId")
        }
        // You can now use this userId throughout your app, e.g., by passing it to ViewModels or other components.

        val database = Firebase.database // Get database instance
        enableEdgeToEdge()
        setContent {
            WhenBabeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MainScreen(userId = userId, database = database) // Pass userId and database
                    }
                    // If MainScreen needs the userId, you can pass it as a parameter
                  
                }
            }
        }
    }
}
