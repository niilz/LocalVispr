package dev.localvispr.models

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface DownloadStatus {
    data object Idle : DownloadStatus

    data class Running(val bytesRead: Long, val totalBytes: Long) : DownloadStatus {
        val fraction: Float?
            get() =
                if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    data class Failed(val message: String) : DownloadStatus
}

class ModelDownloader(private val repository: ModelRepository) {

    private val _statusByModel = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val statusByModel: StateFlow<Map<String, DownloadStatus>> = _statusByModel.asStateFlow()

    suspend fun download(spec: ModelSpec): Result<Unit> =
        withContext(Dispatchers.IO) {
            setStatus(spec.id, DownloadStatus.Running(0L, 0L))
            runCatching {
                    repository.install(
                        spec = spec,
                        openStream = { url -> openFollowRedirects(url) },
                        onProgress = { read, total ->
                            setStatus(spec.id, DownloadStatus.Running(read, total))
                        },
                    )
                }
                .onSuccess { setStatus(spec.id, DownloadStatus.Idle) }
                .onFailure { err ->
                    setStatus(
                        spec.id,
                        DownloadStatus.Failed(err.message ?: err.javaClass.simpleName),
                    )
                }
        }

    // Hugging Face redirects `/resolve/main/...` to a CDN on a different host;
    // HttpURLConnection only auto-follows same-protocol redirects when configured,
    // so we follow 301/302/307/308 manually across hosts.
    private fun openFollowRedirects(initialUrl: String) =
        generateSequence(initialUrl) { null }
            .let {
                var current = initialUrl
                repeat(6) {
                    val currentUri = URI.create(current)
                    val conn =
                        (currentUri.toURL().openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = false
                            connectTimeout = 20_000
                            readTimeout = 60_000
                            setRequestProperty("User-Agent", "LocalVispr/0.1")
                        }
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location =
                            conn.getHeaderField("Location")
                                ?: error("Redirect without Location header")
                        current = currentUri.resolve(location).toString()
                        conn.disconnect()
                    } else if (code in 200..299) {
                        return@let conn.inputStream to conn.contentLengthLong
                    } else {
                        conn.disconnect()
                        error("HTTP $code while downloading $current")
                    }
                }
                error("Too many redirects for $initialUrl")
            }

    private fun setStatus(modelId: String, status: DownloadStatus) {
        _statusByModel.value = _statusByModel.value + (modelId to status)
    }
}
