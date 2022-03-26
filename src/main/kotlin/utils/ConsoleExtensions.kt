package utils

fun printf(format: String, vararg objects: Any?) {
    System.out.printf(format, *objects)
}

fun printlnErr(message: Any?) {
    System.err.println(message)
}