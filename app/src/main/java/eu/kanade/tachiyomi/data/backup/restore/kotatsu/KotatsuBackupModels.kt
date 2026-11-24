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
 * The archive is a plain zip whose entries are JSON arrays named without a file extension
 * (`favourites`, `categories`, `history`, `index`, `bookmarks`, `settings`, ...). Some builds
 * write the same names with a `.json` suffix, which [KotatsuBackupConverter] handles.
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
    @SerialName("alt_title") val altTitle: String? = null,
    val url: String = "",
    @SerialName("public_url") val publicUrl: String = "",
    val rating: Float = 0f,
    val nsfw: Boolean = false,
    @SerialName("content_rating") val contentRating: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("large_cover_url") val largeCoverUrl: String? = null,
    val state: String? = null,
    val author: String? = null,
    val source: String = "",
    // Array of tag objects in current Kotatsu, plain strings in old backups.
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
    val branch: String? = null,
)

@Serializable
data class KotatsuCategory(
    @SerialName("category_id") val categoryId: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("sort_key") val sortKey: Int = 0,
    val title: String = "",
)

@Serializable
data class KotatsuFavourite(
    @SerialName("manga_id") val mangaId: Long = 0,
    @SerialName("category_id") val categoryId: Long = 0,
    @SerialName("sort_key") val sortKey: Int = 0,
    val pinned: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0,
    val manga: KotatsuManga? = null,
)

@Serializable
data class KotatsuHistory(
    @SerialName("manga_id") val mangaId: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("chapter_id") val chapterId: Long = 0,
    val page: Int = 0,
    val scroll: Double = 0.0,
    val percent: Float = 0f,
)

@Serializable
data class KotatsuIndexEntry(
    @SerialName("manga_id") val mangaId: Long = 0,
    val chapters: List<KotatsuChapter> = emptyList(),
)
