package yokai.core.migration.migrations

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.util.system.withIOContext
import yokai.core.migration.Migration
import yokai.core.migration.MigrationContext
import yokai.domain.extension.repo.ExtensionRepoRepository

/**
 * Adds the Keiyoushi extension repository so the extension list is populated on a fresh install,
 * without the user having to paste the URL.
 *
 * The seeding happens once: the preference is set afterwards, so a repository the user deletes on
 * purpose is not added back on the next start.
 */
class DefaultExtensionRepoMigration : Migration {

    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val repoRepository: ExtensionRepoRepository = migrationContext.get() ?: return@withIOContext false
        val preferenceStore: PreferenceStore = migrationContext.get() ?: return@withIOContext false

        val seeded = preferenceStore.getBoolean(PreferenceKeys.keiyoushiRepoSeeded, false)
        if (seeded.get()) return@withIOContext true

        val existing = repoRepository.getRepository(BASE_URL)
        try {
            if (existing == null) {
                repoRepository.insertRepository(BASE_URL, NAME, null, WEBSITE, FINGERPRINT)
            } else if (existing.name != NAME || existing.signingKeyFingerprint != FINGERPRINT) {
                repoRepository.upsertRepository(BASE_URL, NAME, existing.shortName, WEBSITE, FINGERPRINT)
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to add the default extension repository" }
            return@withIOContext false
        }

        seeded.set(true)
        return@withIOContext true
    }

    companion object {
        private const val BASE_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        private const val NAME = "Keiyoushi"
        private const val WEBSITE = "https://keiyoushi.github.io"
        private const val FINGERPRINT = "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"
    }
}
