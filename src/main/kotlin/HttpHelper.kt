import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import utils.await
import java.io.IOException

private val httpClient = OkHttpClient.Builder()
    .dispatcher(
        Dispatcher().apply {
            maxRequestsPerHost = 10
            maxRequests = 25
        })
    .build()

suspend fun getRaw(url: String): String {
    val request = Request.Builder().url(url).build()
    return httpClient.newCall(request).await().use { response ->
        if (response.code < 200 || response.code >= 300) {
            throw HttpStatusException(response.message, response.code, request.url.toString())
        }
        response.body?.string() ?: throw IOException("Response has no body")
    }
}

suspend fun getSite(url: String): Document = Jsoup.parse(getRaw(url))