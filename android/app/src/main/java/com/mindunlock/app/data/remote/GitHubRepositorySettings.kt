package com.mindunlock.app.data.remote

import android.content.Context

data class GitHubRepositorySettings(
    val owner: String,
    val repository: String,
    val branch: String,
    val manifestPath: String
)

object GitHubRepositorySettingsStore {
    private const val PREFS = "mindunlock_github_repository"
    private const val OWNER = "owner"
    private const val REPOSITORY = "repository"
    private const val BRANCH = "branch"
    private const val MANIFEST = "manifest_path"

    fun read(context: Context): GitHubRepositorySettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return GitHubRepositorySettings(
            owner = prefs.getString(OWNER, null)?.takeIf(String::isNotBlank) ?: "MrUtility01",
            repository = prefs.getString(REPOSITORY, null)?.takeIf(String::isNotBlank) ?: "MindUnlock",
            branch = prefs.getString(BRANCH, null)?.takeIf(String::isNotBlank) ?: "main",
            manifestPath = prefs.getString(MANIFEST, null)?.takeIf(String::isNotBlank) ?: "content/manifest.json"
        )
    }


    fun parseRepositoryReference(value: String): Pair<String, String> {
        val raw = value.trim().trimEnd('/')
        val normalized = when {
            raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true) -> {
                val uri = java.net.URI(raw)
                require(uri.scheme.equals("https", true) && uri.host.equals("github.com", true) &&
                    uri.userInfo == null && uri.query == null && uri.fragment == null) {
                    "فقط نشانی امن مخزن در github.com پذیرفته می‌شود."
                }
                uri.path.trim('/').split('/').take(2).joinToString("/")
            }
            else -> raw
        }
        val parts = normalized.split('/')
        require(parts.size == 2 && parts.all { it.isNotBlank() }) {
            "مسیر مخزن را به شکل owner/repository یا https://github.com/owner/repository وارد کن."
        }
        return parts[0] to parts[1].removeSuffix(".git")
    }

    fun save(context: Context, owner: String, repository: String, branch: String, manifestPath: String) {
        val cleanOwner = owner.trim()
        val cleanRepository = repository.trim()
        val cleanBranch = branch.trim()
        val cleanManifest = manifestPath.trim().replace('\\', '/').trim('/')
        require(cleanOwner.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})"))) { "نام مالک GitHub معتبر نیست." }
        require(cleanRepository.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "نام مخزن GitHub معتبر نیست." }
        require(cleanBranch.isNotBlank() && cleanBranch.length <= 250 && !cleanBranch.startsWith("/") &&
            cleanBranch.split('/').none { it.isBlank() || it == "." || it == ".." }) { "نام شاخه GitHub معتبر نیست." }
        require(cleanManifest.isNotBlank() && cleanManifest.length <= 500 &&
            cleanManifest.split('/').none { it.isBlank() || it == "." || it == ".." }) { "مسیر Manifest معتبر نیست." }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(OWNER, cleanOwner)
            .putString(REPOSITORY, cleanRepository)
            .putString(BRANCH, cleanBranch)
            .putString(MANIFEST, cleanManifest)
            .apply()
    }
}
