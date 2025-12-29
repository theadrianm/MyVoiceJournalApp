package adrian.sasha.myvoicejournalapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.IOException
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class OpenAIClient(
    private val apiKey: String,
) {
    private val http = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun transcribeAudio(audioFile: File): String {
        // Uses OpenAI Audio Transcriptions endpoint (multipart/form-data)
        val url = "https://api.openai.com/v1/audio/transcriptions"

        val fileBody = audioFile.asRequestBody("audio/mp4".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "gpt-4o-mini-transcribe")
            .addFormDataPart("file", audioFile.name, fileBody)
            .build()

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        val resp = req.await()
        val parsed = json.decodeFromString(TranscriptionResponse.serializer(), resp)
        return parsed.text.trim()
    }

    suspend fun analyzeTranscript(transcript: String): AnalysisResult {
        // Uses Responses API for JSON-only output
        val url = "https://api.openai.com/v1/responses"

        val bodyObj = ResponsesRequest(
            model = "gpt-4o-mini",
            input = listOf(
                InputMsg(
                    role = "system",
                    content = listOf(
                        ContentText(
                            type = "text",
                            text = "Analyze a private journal transcript. Return STRICT JSON ONLY with keys: sentiment (number -1..1), topics (array of 1-5 short strings), summaryInsight (one short sentence). No extra text."
                        )
                    )
                ),
                InputMsg(
                    role = "user",
                    content = listOf(ContentText(type = "text", text = transcript.take(12000)))
                )
            )
        )

        val bodyJson = json.encodeToString(ResponsesRequest.serializer(), bodyObj)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = req.await()
        val parsed = json.decodeFromString(ResponsesResponse.serializer(), resp)

        // output_text is easiest; sometimes model returns JSON in output_text
        val outputText = parsed.outputText ?: "{}"
        val clean = outputText.trim()

        return try {
            json.decodeFromString(AnalysisResult.serializer(), clean)
                .normalize()
        } catch (e: Exception) {
            AnalysisResult(sentiment = 0.0, topics = emptyList(), summaryInsight = "No insight.")
        }
    }

    private suspend fun Request.await(): String = suspendCoroutine { cont ->
        http.newCall(this).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    if (!it.isSuccessful) {
                        cont.resumeWithException(RuntimeException("HTTP ${it.code}: $body"))
                    } else cont.resume(body)
                }
            }
        })
    }
}

@Serializable
data class TranscriptionResponse(val text: String)

@Serializable
data class AnalysisResult(
    val sentiment: Double,
    val topics: List<String>,
    val summaryInsight: String
) {
    fun normalize(): AnalysisResult {
        val s = sentiment.coerceIn(-1.0, 1.0)
        val t = topics.take(5).map { it.trim() }.filter { it.isNotBlank() }
        val si = summaryInsight.take(200)
        return copy(sentiment = s, topics = t, summaryInsight = si)
    }
}

@Serializable
data class ResponsesRequest(
    val model: String,
    val input: List<InputMsg>
)

@Serializable
data class InputMsg(
    val role: String,
    val content: List<ContentText>
)

@Serializable
data class ContentText(
    val type: String,
    val text: String
)

@Serializable
data class ResponsesResponse(
    @SerialName("output_text") val outputText: String? = null
)

// helper
private fun String.toRequestBody(mediaType: MediaType) =
    RequestBody.create(mediaType, this)
