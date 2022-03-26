import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.parser.Parser.xmlParser
import utils.printf
import utils.printlnErr
import utils.truncate
import java.io.IOException
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

const val BASE_URL = "https://ufind.univie.ac.at"
const val SEMESTER = "2022S"
const val MAX_DATES = 10
val INSTANCES = setOf("https://m1-ufind.univie.ac.at/", "https://m2-ufind.univie.ac.at/")

fun main(args: Array<String>): Unit = runBlocking {
    // Get course id from first arg or read from stdin
    val selectedCourseID = args.getOrElse(0) {
        print("Please enter a direction of study id (e.g. 0.01): ")
        readlnOrNull()
    }

    if (selectedCourseID?.matches(Regex("^[0-9]?[0-9].[0-9]{2}$")) != true) {
        printlnErr(
            """
            '$selectedCourseID' is not a valid direction of study.
            Please specify a direction of study listed on https://ufind.univie.ac.at/de/vvz.html
            For example '0.01' for individual studies.
        """.trimIndent()
        )
        exitProcess(1)
    }


    try {
        start(selectedCourseID)
    } catch (httpEx: HttpStatusException) {
        printlnErr("HTTP Error: ${httpEx.statusCode} ${httpEx.message} (${httpEx.url})")
        exitProcess(2)
    } catch (ioEx: IOException) {
        printlnErr("IO Error (Network): ${ioEx.message}")
        exitProcess(2)
    }

    exitProcess(0)
}

suspend fun start(selectedCourseID: String) {
    val courseOverviewPath = getCourseOverviewPath(selectedCourseID)
    if (courseOverviewPath == null) {
        println("Could not find Course with number '$selectedCourseID'")
        exitProcess(1)
    }

    val lvDates = getLvDates(courseOverviewPath)

    val tableFormat =
        "%-10s | %-5s | %-5s | %-4s | %-50s | %s%n" // e.g 16.04.2021 | 10:00 | 12:00 | Digital | VO | Informatik
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    printf(tableFormat, "Date", "Start", "End", "Type", "Name", "Location")
    println("-----------+-------+-------+------+${"-".repeat(52)}+${"-".repeat(52)}")
    for (lv in lvDates) {
        printf(
            tableFormat,
            lv.start.format(dateFormatter),
            lv.start.format(timeFormatter),
            lv.end.format(timeFormatter),
            lv.type,
            lv.name.truncate(50),
            lv.location
        )
    }
}

suspend fun getCourseOverviewPath(selectedCourseID: String): String? {
    val selectedMainCourseID = selectedCourseID.split('.')[0]
    val doc = getSite("$BASE_URL/cache/de/main.html")

    val pathRegex = Regex("(?<=path=)([0-9]+)")
    return doc.select(".usse-id-vvz")
        .find {
            it.selectFirst("div div a")?.text()?.startsWith("$selectedMainCourseID.") == true
        }
        ?.select("div div a")
        ?.filter { it.text().startsWith("$selectedCourseID ") }
        ?.firstNotNullOf {
            pathRegex.find(it.attr("href"))?.groupValues?.getOrNull(1)
        }
}

suspend fun getLvDates(courseOverviewPath: String): List<LvDate> = coroutineScope {
    val doc = getSite("$BASE_URL/cache/de/$courseOverviewPath.html")

    val lvDates = doc.select(".list.course .number")
        .map { it.text() }
        .map { courseNumber ->
            async {
                val raw = getRaw("${INSTANCES.random()}courses/$courseNumber/$SEMESTER")
                val xml = Jsoup.parse(raw, "/", xmlParser())
                xml.extractLvDates()
            }
        }
        .awaitAll()
        .filterNotNull()
        .flatten()
        .toSet() // Use a set so that Same course at same time and location is not displayed twice

    return@coroutineScope lvDates.sortedBy { it.start }.take(MAX_DATES)
}