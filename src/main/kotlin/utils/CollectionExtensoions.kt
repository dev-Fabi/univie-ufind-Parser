package utils

fun <T> Collection<T>?.emptyToNull(): Collection<T>? = if (this.isNullOrEmpty()) null else this