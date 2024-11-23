package snd.komf.model

import kotlinx.serialization.Serializable

@Serializable
data class Author(
    val name: String,
    val role: AuthorRole? = null
)
enum class AuthorRole {
    WRITER,
    PENCILLER,
    INKER,
    COLORIST,
    LETTERER,
    COVER,
    EDITOR,
    TRANSLATOR
}
