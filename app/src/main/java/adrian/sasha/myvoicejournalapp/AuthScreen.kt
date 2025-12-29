package adrian.sasha.myvoicejournalapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(onAuthed: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp)) {
        Text("MyVoiceJournal", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            auth.signInWithEmailAndPassword(email.trim(), pass).await()
                            onAuthed()
                        } catch (e: Exception) {
                            error = e.message ?: "Sign-in failed"
                        } finally {
                            loading = false
                        }
                    }
                }
            ) { Text("Sign in") }

            OutlinedButton(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            auth.createUserWithEmailAndPassword(email.trim(), pass).await()
                            onAuthed()
                        } catch (e: Exception) {
                            error = e.message ?: "Sign-up failed"
                        } finally {
                            loading = false
                        }
                    }
                }
            ) { Text("Sign up") }
        }

        if (loading) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
