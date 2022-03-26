package utils

fun String.truncate(maxLength: Int, endString: String = "..."): String {
    return if (this.length <= maxLength) {
        this
    } else {
        this.take(maxLength - endString.length) + endString
    }
}