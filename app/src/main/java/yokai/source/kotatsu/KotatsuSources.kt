package yokai.source.kotatsu

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import org.koitharu.kotatsu.parsers.DirectParsersAccess
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.core.ParsersBundleImpl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The Kotatsu-Redo parsers built into the app.
 *
 * They are exposed as ordinary sources, so they are browsed, searched, followed and read by the
 * same screens as extension sources. Parsers that Kotatsu itself marks as broken, and sources
 * without pages (novels), are left out.
 */
object KotatsuSources {

    @OptIn(DirectParsersAccess::class)
    val bundle: ParsersBundle = ParsersBundleImpl

    fun create(): List<CatalogueSource> {
        val loader = KotatsuLoaderContext(Injekt.get<NetworkHelper>())
        return bundle.sources
            .filter { it.hasPages && !it.isBroken }
            .map { KotatsuCatalogueSource(it, loader) }
    }
}
