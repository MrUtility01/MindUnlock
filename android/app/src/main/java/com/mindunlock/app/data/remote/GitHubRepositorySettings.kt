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
            branch = prefs.getString(BRANCH, null)?.takeIf(String::isNotBlank) ?: "feature/github-public-browser",
            manifestPath = prefs.getString(MANIFEST, null)?.takeIf(String::isNotBlank) ?: "content/manifest.json"
        )
    }

    fun save(context: Context, owner: String, repository: String, branch: String, manifestPath: String) {
        val cleanOwner = owner.trim()
        val cleanRepository = repository.trim()
        val cleanBranch = branch.trim()
        val cleanManifest = manifestPath.trim().replace('\\\\', '/').trim('/')
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
