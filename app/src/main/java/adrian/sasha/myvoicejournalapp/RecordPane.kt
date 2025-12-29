package adrian.sasha.myvoicejournalapp

import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun RecordPane() {
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val db = remember { FirebaseFirestore.getInstance() }
    val openai = remember { OpenAIClient(BuildConfig.OPENAI_API_KEY) }

    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Idle") }
    var error by remember { mutableStateOf<String?>(null) }

    var currentFile by remember { mutableStateOf<File?>(null) }

    val monthId = remember {
        YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
    }

    fun startRecording(output: File) {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder() else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44100)
        r.setAudioEncodingBitRate(128000)
        r.setOutputFile(output.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        isRecording = true
        status = "Recording..."
    }

    fun stopRecording() {
        val r = recorder ?: return
        try {
            r.stop()
        } finally {
            r.release()
            recorder = null
            isRecording = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Record a voice entry", style = MaterialTheme.typography.titleMedium)
        Text(
            "Transcript is never shown. Only analysis is saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isRecording) {
            Button(onClick = {
                stopRecording()
                val localFile = currentFile
                if (localFile == null) {
                    status = "Idle"
                    return@Button
                }

                scope.launch {
                    error = null
                    try {
                        status = "Transcribing..."
                        val transcript = openai.transcribeAudio(localFile)

                        status = "Analyzing..."
                        val analysis = openai.analyzeTranscript(transcript)

                        status = "Saving insights..."
                        val entryId = UUID.randomUUID().toString()

                        // Save ONLY analysis (no transcript)
                        val entryDoc = mapOf(
                            "createdAt" to com.google.firebase.Timestamp.now(),
                            "sentiment" to analysis.sentiment,
                            "topics" to analysis.topics,
                            "summaryInsight" to analysis.summaryInsight,
                            "monthId" to monthId
                        )

                        db.collection("users").document(uid)
                            .collection("entries").document(entryId)
                            .set(entryDoc).await()

                        // Update simple monthly stats
                        val statsRef = db.collection("users").document(uid)
                            .collection("stats").document("monthly_$monthId")

                        db.runTransaction { tx ->
                            val snap = tx.get(statsRef)
                            val cur = if (snap.exists()) snap.data else emptyMap<String, Any>()

                            val prevTotal = (cur?.get("totalEntries") as? Number)?.toLong() ?: 0L
                            val total = prevTotal + 1L

                            val prevAvg = (cur?.get("avgSentiment") as? Number)?.toDouble() ?: 0.0
                            val avg = ((prevAvg * prevTotal) + analysis.sentiment) / total

                            val topicCounts = (cur?.get("topicCounts") as? Map<*, *>)?.mapNotNull { (k, v) ->
                                val kk = k as? String ?: return@mapNotNull null
                                val vv = (v as? Number)?.toLong() ?: 0L
                                kk to vv
                            }?.toMap()?.toMutableMap() ?: mutableMapOf()

                            analysis.topics.forEach { t ->
                                topicCounts[t] = (topicCounts[t] ?: 0L) + 1L
                            }

                            val topTopics = topicCounts.entries
                                .sortedByDescending { it.value }
                                .take(8)
                                .map { it.key }

                            val comparisons = (cur?.get("comparisons") as? List<*>)?.filterIsInstance<String>()?.toMutableList()
                                ?: mutableListOf()
                            if (analysis.topics.isNotEmpty()) {
                                comparisons.add(0, "You mentioned “${analysis.topics[0]}” again.")
                            }

                            tx.set(statsRef, mapOf(
                                "monthId" to monthId,
                                "totalEntries" to total,
                                "avgSentiment" to avg,
                                "topicCounts" to topicCounts,
                                "topTopics" to topTopics,
                                "comparisons" to comparisons.take(10),
                                "updatedAt" to com.google.firebase.Timestamp.now()
                            ), com.google.firebase.firestore.SetOptions.merge())
                            null
                        }.await()

                        // Delete local audio after processing (optional)
                        localFile.delete()

                        status = "Saved. Check Insights."
                    } catch (e: Exception) {
                        error = e.message ?: "Failed"
                        status = "Idle"
                    } finally {
                        currentFile = null
                    }
                }
            }) { Text("Stop & Save") }
        } else {
            Button(onClick = {
                error = null
                val file = File.createTempFile("entry_", ".m4a")
                currentFile = file
                startRecording(file)
            }) { Text("Start Recording") }
        }

        Text("Status: $status")

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
