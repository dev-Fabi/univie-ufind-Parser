import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import utils.emptyToNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class LvDate(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String,
    val name: String,
    val type: String
)

fun Document.extractLvDates(): Collection<LvDate>? {
    fun String.toLocalDate() = LocalDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun Element.extractLocation(): String {
        return this.select("location").emptyToNull()?.joinToString(", ") {
            val room = it.selectFirst("room")?.text() ?: ""

            if (room == "Digital" || room == "Hybride Lehre") {
                room
            } else {
                val address = it.selectFirst("address")?.text() ?: ""
                "$room $address"
            }
        }?.trim() ?: "undefined"
    }

    val group = this.selectFirst("course groups group") ?: return null

    val lvDates =
        mutableSetOf<LvDate>() // Use a set so that Same course at same time and location is not displayed twice

    val name = this.selectFirst("longname")?.text() ?: "No name found"
    val type = this.selectFirst("type")?.text().orEmpty()

    for (event in group.select("wwlong wwevent")) {
        val start = event.attr("begin").toLocalDate()
        if (start.isAfter(LocalDateTime.now())) {
            val location = event.extractLocation()
            val end = event.attr("end").toLocalDate()
            lvDates.add(LvDate(start, end, location, name, type))

            // Return as max shown Dates collected (No need to parse the remaining events)
            if (lvDates.size >= MAX_DATES) return lvDates
        }
    }

    return lvDates
}
