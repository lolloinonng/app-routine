package com.routine.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureFirebase()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent { MaterialTheme { App() } }
    }

    private fun ensureFirebase() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            // Stesso progetto del sito: app e PC condividono auth + database
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApiKey("AIzaSyAzjGvo43AQpvGrDjIpJ1YCxTs0wljn6cI")
                    .setApplicationId("1:417271086293:web:057448370e230bfb94765c")
                    .setProjectId("routine-d2347")
                    .setDatabaseUrl("https://routine-d2347-default-rtdb.europe-west1.firebasedatabase.app")
                    .build(),
            )
        }
    }
}

@Composable
fun App() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(Unit) {
        val l = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(l)
        onDispose { auth.removeAuthStateListener(l) }
    }
    val u: FirebaseUser? = user
    if (u == null) AuthScreen(auth) else WeekScreen(
        uid = u.uid,
        email = u.email,
        onLogout = { auth.signOut() },
    )
}

@Composable
fun AuthScreen(auth: FirebaseAuth) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Routine", style = MaterialTheme.typography.headlineMedium)
        Text("Accedi per sincronizzare con il sito", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TextField(
            value = pass, onValueChange = { pass = it }, label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                error = ""
                if (email.isBlank() || pass.isEmpty()) { error = "Compila email e password"; return@Button }
                busy = true
                if (register) auth.createUserWithEmailAndPassword(email.trim(), pass)
                    .addOnCompleteListener { busy = false; if (!it.isSuccessful) error = it.exception?.localizedMessage ?: "Errore" }
                else auth.signInWithEmailAndPassword(email.trim(), pass)
                    .addOnCompleteListener { busy = false; if (!it.isSuccessful) error = it.exception?.localizedMessage ?: "Credenziali non valide" }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (register) "Registrati" else "Accedi") }
        TextButton(onClick = { register = !register; error = "" }) {
            Text(if (register) "Hai già un account? Accedi" else "Non hai un account? Registrati")
        }
    }
}
