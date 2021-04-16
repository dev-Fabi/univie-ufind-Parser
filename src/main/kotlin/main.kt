import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.concurrent.*
import java.util.regex.Pattern
import kotlin.system.exitProcess

const val BASE_URL = "https://ufind.univie.ac.at"
const val SEMESTER = "2021S"
const val MAX_DATES = 10

val httpClient = OkHttpClient()

fun main(args: Array<String>) {
    if (args.size != 1 || !Regex("^[0-9]?[0-9].[0-9]{2}$").matches(args[0])) {
        println("Argument required!")
        println("Please specify a direction of study listed on https://ufind.univie.ac.at/de/vvz.html")
        println("For example '0.01' for individual studies")
        exitProcess(1)
    }

    val selectedCourseID = args[0]

    try {
        val courseOverviewPath = getCourseOverviewPath(selectedCourseID)
        if (courseOverviewPath == null) {
            println("Could not find Course with number '$selectedCourseID'")
            exitProcess(1)
        }

        val lvDates = getLvDates(courseOverviewPath)

        val tableFormat = "%-10s | %-5s | %-5s | %s%n" // e.g 16.04.2021 | 10:00 | 12:00 | Digital
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        System.out.printf(tableFormat, "Date", "Start", "End", "Location")
        println("-----------+-------+-------+---------")
        for (lv in lvDates) {
            System.out.printf(
                tableFormat,
                lv.start.format(dateFormatter),
                lv.start.format(timeFormatter),
                lv.end.format(timeFormatter),
                lv.location
            )
        }
    } catch (nEx: NetworkException) {
        println("Network Error: ${nEx.message}")
        exitProcess(2)
    }
}

@Throws(NetworkException::class)
fun getCourseOverviewPath(selectedCourseID: String): String? {
    val selectedMainCourseID = selectedCourseID.split('.')[0]
    val courseListRequest = Request.Builder()
        .url("$BASE_URL/cache/de/main.html")
        .build()

    try {
        httpClient.newCall(courseListRequest).execute().use { response ->
            checkResponse(response)

            val doc = Jsoup.parse(response.body?.string() ?: "")

            val elements = doc.select(".usse-id-vvz")
            for (element in elements) {
                val courseID: String? = element.selectFirst("div div a")?.text()?.split(' ')?.get(0)
                if (courseID == null || courseID.split('.')[0] != selectedMainCourseID) {
                    continue
                }

                for (courseLink in element.select("div div a")) {
                    if (courseLink.text().split(' ')[0] == selectedCourseID) {
                        val pattern = Pattern.compile("(?<=path=)[0-9]*")
                        val matcher = pattern.matcher(courseLink.attr("href"))
                        if (matcher.find()) {
                            println("Selected Course: ${courseLink.text()}")
                            return matcher.group(0)
                        }
                    }
                }
            }
        }
    } catch (nEx: NetworkException) {
        throw nEx
    } catch (ex: IOException) {
        val nEx = NetworkException(ex.message)
        nEx.initCause(ex)
        throw nEx
    }

    return null
}

@Throws(NetworkException::class)
fun getLvDates(courseOverviewPath: String): List<LvDate> {
    val coursesRequest = Request.Builder()
        .url("$BASE_URL/cache/de/$courseOverviewPath.html")
        .build()

    //use five threads per CPU (as tasks are IO "intensive")
    val threadPool: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*5)
    val service: CompletionService<List<LvDate>> = ExecutorCompletionService(threadPool)

    try {
        httpClient.newCall(coursesRequest).execute().use { response ->
            checkResponse(response)

            val doc = Jsoup.parse(response.body?.string() ?: "")

            //add separate task for each course
            doc.select(".list.course .number").forEach {
                service.submit(LvDateTask(it.text()))
            }
        }
    } catch (nEx: NetworkException) {
        throw nEx
    } catch (ex: IOException) {
        val nEx = NetworkException(ex.message)
        nEx.initCause(ex)
        throw nEx
    }

    threadPool.shutdown()

    val lvDates = ArrayList<LvDate>()
    try {
        while (!threadPool.isTerminated) {
            val lvs: List<LvDate>? = service.take().get()
            if (lvs?.isNotEmpty() == true) {
                lvDates.addAll(lvs)
            }
        }
    } catch (eEx: ExecutionException) {
        //stop all other tasks as network-problems will effect all of them
        threadPool.shutdownNow()

        val e = eEx.cause
        if (e is NetworkException)
            throw e

        throw eEx
    }

    lvDates.sortBy { it.start }

    if (lvDates.size <= MAX_DATES)
        return lvDates

    return lvDates.subList(0, MAX_DATES)
}

@Throws(NetworkException::class)
fun checkResponse(response: Response) {
    if (response.code < 200 || response.code >= 300) {
        throw NetworkException(response.message)
    }
}