import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import kotlin.random.Random


class LvDateTask(private val courseNumber: String) : Callable<List<LvDate>?> {

    @Throws(NetworkException::class)
    override fun call(): List<LvDate>? {
        val httpClient = OkHttpClient()
        val request = Request.Builder()
            .url("${INSTANCES[Random.nextInt(INSTANCES.size)]}/courses/${this.courseNumber}/$SEMESTER")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                checkResponse(response)

                val xml = response.body?.string() ?: return null
                val doc = Jsoup.parse(xml, "/", Parser.xmlParser())
                val group = doc.selectFirst("course groups group") ?: return null

                return this.extractFromEvent(group.select("wwlong wwevent"))
            }
        } catch (nEx: NetworkException) {
            throw nEx
        } catch (ex: IOException) {
            val nEx = NetworkException(ex.message)
            nEx.initCause(ex)
            throw nEx
        }
    }

    private fun extractLocation(eventXml: Element): String {
        return eventXml.select("location")?.joinToString(", ") {
            val room = it.selectFirst("room")?.text() ?: ""

            val location = if (room == "Digital" || room == "Hybride Lehre") {
                room
            } else {
                val address = it.selectFirst("address")?.text() ?: ""
                "$room $address"
            }

            location
        } ?: "undefined"
    }

    private fun toLocalDate(dateString: String): LocalDateTime {
        return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun extractFromEvent(events: Elements): List<LvDate>? {
        if (events.size < 1) return null

        val lvDates = ArrayList<LvDate>()
        for (event in events) {
            val start = this.toLocalDate(event.attr("begin"))
            if (start.isAfter(LocalDateTime.now())) {
                val location = this.extractLocation(event)
                val end = this.toLocalDate(event.attr("end"))
                lvDates.add(LvDate(start, end, location))

                //return as max shown Dates collected
                if (lvDates.size >= MAX_DATES) return lvDates
            }
        }

        return lvDates
    }
}