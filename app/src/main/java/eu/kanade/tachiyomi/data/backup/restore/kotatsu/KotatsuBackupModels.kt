package eu.kanade.tachiyomi.data.backup.restore.kotatsu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON models for the Kotatsu / Kotatsu Redo `.bk.zip` backup.
 *
 * The archive is a zip of JSON arrays named after the data they hold (`favourites`, `categories`,
 * `history`, `index`, ...), without a file extension in current builds. Unknown keys are ignored,
 * so only the fields used by [KotatsuBackupConverter] are declared here.
 */
internal val kotatsuJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class KotatsuManga(
    val id: Long = 0,
    val title: String = "",
    val url: String = "",
    @SerialName("public_url") val publicUrl: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("large_cover_url") val largeCoverUrl: String? = null,
    val state: String? = null,
    val author: String? = null,
    val source: String = "",
    // Tag objects in current Kotatsu, plain strings in older backups.
    val tags: List<JsonElement> = emptyList(),
) {
    val tagTitles: List<String>
        get() = tags.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
                is JsonObject -> element["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
}

@Serializable
data class KotatsuChapter(
    val id: Long = 0,
    val name: String = "",
    val number: Float = 0f,
    val url: String = "",
    val scanlator: String? = null,
    @SerialName("upload_date") val uploadDate: Long = 0,
)

@Serializable
data class KotatsuCategory(
    @SerialName("category_id") val categoryId: Long = 0,
    @SerialName("sort_key") val sortKey: Int = 0,
    val title: String = "",
)

@Serializable
data class KotatsuFavourite(
    @SerialName("manga_id") val mangaId: Long = 0,
    @SerialName("category_id") val categoryId: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    val manga: KotatsuManga? = null,
)

@Serializable
data class KotatsuHistory(
    @SerialName("manga_id") val mangaId: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("chapter_id") val chapterId: Long = 0,
    val page: Int = 0,
)

@Serializable
data class KotatsuIndexEntry(
    @SerialName("manga_id") val mangaId: Long = 0,
    val chapters: List<KotatsuChapter> = emptyList(),
)
