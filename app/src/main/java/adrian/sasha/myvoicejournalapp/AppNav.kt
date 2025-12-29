package adrian.sasha.myvoicejournalapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.compose.NavHost
import com.google.firebase.auth.FirebaseAuth

sealed class Route(val v: String) {
    data object Auth : Route("auth")
    data object Home : Route("home")
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNav(onRequestMic: () -> Unit) {
    val nav = rememberNavController()
    val auth = remember { FirebaseAuth.getInstance() }

    val start = if (auth.currentUser == null) Route.Auth.v else Route.Home.v

    NavHost(navController = nav, startDestination = start) {
        composable(Route.Auth.v) {
            AuthScreen(
                onAuthed = {
                    nav.navigate(Route.Home.v) {
                        popUpTo(Route.Auth.v) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.Home.v) {
            HomeScreen(
                onLogout = {
                    auth.signOut()
                    nav.navigate(Route.Auth.v) {
                        popUpTo(Route.Home.v) { inclusive = true }
                    }
                },
                onRequestMic = onRequestMic
            )
        }
    }
}
