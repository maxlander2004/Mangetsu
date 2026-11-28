package yokai.source.kotatsu

import android.graphics.Canvas
import android.graphics.Paint
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * What a Kotatsu parser needs from its host: an HTTP client, cookies, a user agent and a source
 * config. The app's own [NetworkHelper] supplies the first three, so Kotatsu sources share the
 * cookie jar and the Cloudflare handling the extension sources already use.
 *
 * JavaScript evaluation and WebView interception are not available to sources here. Parsers that
 * depend on them report their usual error instead of taking the app down with them.
 */
class KotatsuLoaderContext(
    private val network: NetworkHelper,
) : MangaLoaderContext() {

    override val httpClient: OkHttpClient
        get() = network.client

    override val cookieJar: CookieJar
        get() = network.cookieJar

    override val parsers: ParsersBundle
        get() = KotatsuSources.bundle

    @Suppress("OVERRIDE_DEPRECATION")
    override suspend fun evaluateJs(script: String): String? = null

    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? = null

    override fun getConfig(source: MangaSource): MangaSourceConfig = DefaultSourceConfig

    override fun getDefaultUserAgent(): String = network.defaultUserAgent

    /**
     * Page redrawing is only used by a handful of parsers that unscramble images. Passing the
     * response through keeps them readable as the site serves them.
     */
    override fun redrawImageResponse(response: Response, redraw: (Bitmap) -> Bitmap): Response = response

    override fun createBitmap(width: Int, height: Int): Bitmap =
        AndroidBitmap(android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888))

    /** Nothing is persisted yet: every key answers with the value the parser declares for it. */
    private object DefaultSourceConfig : MangaSourceConfig {
        override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
    }

    /** The parsers' bitmap is a canvas the parser can compose into. */
    private class AndroidBitmap(val bitmap: android.graphics.Bitmap) : Bitmap {

        override val width: Int
            get() = bitmap.width

        override val height: Int
            get() = bitmap.height

        override fun drawBitmap(src: Bitmap, srcRect: Rect, dstRect: Rect) {
            val source = (src as? AndroidBitmap)?.bitmap ?: return
            Canvas(bitmap).drawBitmap(
                source,
                android.graphics.Rect(srcRect.left, srcRect.top, srcRect.right, srcRect.bottom),
                android.graphics.Rect(dstRect.left, dstRect.top, dstRect.right, dstRect.bottom),
                Paint(),
            )
        }
    }
}
