package adrian.sasha.myvoicejournalapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class MonthlyStats(
    val monthId: String = "",
    val totalEntries: Long = 0,
    val avgSentiment: Double = 0.0,
    val topTopics: List<String> = emptyList(),
    val comparisons: List<String> = emptyList()
)

@Composable
fun InsightsPane(monthId: String) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = remember { FirebaseFirestore.getInstance() }

    var loading by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf(MonthlyStats(monthId = monthId)) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(monthId) {
        loading = true
        error = null
        try {
            val snap = db.collection("users").document(uid)
                .collection("stats").document("monthly_$monthId")
                .get().await()

            if (snap.exists()) {
                stats = MonthlyStats(
                    monthId = monthId,
                    totalEntries = snap.getLong("totalEntries") ?: 0,
                    avgSentiment = snap.getDouble("avgSentiment") ?: 0.0,
                    topTopics = (snap.get("topTopics") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    comparisons = (snap.get("comparisons") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                )
            } else {
                stats = MonthlyStats(monthId = monthId)
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    if (loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }

    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("This month", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Entries: ${stats.totalEntries}")
            Text("Average tone: ${"%.2f".format(stats.avgSentiment)}  (−1..+1)")

            Spacer(Modifier.height(12.dp))
            Text("Top topics", style = MaterialTheme.typography.titleSmall)
            if (stats.topTopics.isEmpty()) Text("No topics yet.")
            else stats.topTopics.take(5).forEach { Text("• $it") }

            Spacer(Modifier.height(12.dp))
            Text("Patterns", style = MaterialTheme.typography.titleSmall)
            if (stats.comparisons.isEmpty()) Text("Record more entries to see changes.")
            else stats.comparisons.take(5).forEach { Text("• $it") }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        "Note: You can’t view transcripts. This app only shows patterns.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
