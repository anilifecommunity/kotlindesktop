package me.enivzoy.anilife.core.error

 




data class ErrorEntry(
    val code: String,
    val message: String,
    val detail: String? = null,
    val step: String? = null,
) {
    companion object {
         
        val Fallback: ErrorEntry = ErrorEntry("DAPP-01", "Something went wrong")

        fun asEntry(raw: String?): ErrorEntry = when {
            raw.isNullOrBlank() -> Fallback
            Regex("^D[A-Z]{2}-\\d+$").matches(raw) -> ErrorEntry(raw, raw)
            else -> Fallback
        }
    }
}

