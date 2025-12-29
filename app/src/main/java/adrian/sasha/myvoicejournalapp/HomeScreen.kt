package adrian.sasha.myvoicejournalapp

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onRequestMic: () -> Unit,
) {
    val ctx = LocalContext.current
    val hasMic = remember {
        ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    var tab by remember { mutableStateOf(0) }
    val monthId = remember {
        YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onLogout) { Text("Logout") }
        }

        Spacer(Modifier.height(12.dp))

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Insights") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Record") })
        }

        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> InsightsPane(monthId = monthId)
            1 -> {
                if (!hasMic) {
                    Text("Mic permission needed to record.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestMic) { Text("Grant mic permission") }
                } else {
                    RecordPane()
                }
            }
        }
    }
}
