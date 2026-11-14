package com.mindunlock.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubManifestPolicyTest {
    private fun manifest(items: String): String = """
        {
          "schema_version": 1,
          "generated_at": "2026-11-15T00:00:00Z",
          "items": $items
        }
    """.trimIndent()

    private fun item(
        id: String = "network-basics",
        path: String = "content/library/network-basics.json",
        hash: String = "a".repeat(64)
    ): String = """
        {
          "id": "$id",
          "title": "مبانی شبکه",
          "group_name": "شبکه",
          "order_num": 1,
          "path": "$path",
          "format": "json",
          "content_hash": "$hash",
          "updated_at": "2026-11-15T00:00:00Z",
          "schema_version": 1
        }
    """.trimIndent()

    @Test
    fun parsesAndSortsValidManifestEntries() {
        val parsed = GitHubManifestPolicy.parse(
            manifest("[${item(id = "second", path = "content/library/second.json")}, ${item()}]")
        )
        assertEquals(2, parsed.size)
        assertEquals("network-basics", parsed.first().id)
        assertEquals("content/library/network-basics.json", parsed.first().path)
    }

    @Test
    fun rejectsUnsupportedManifestSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            GitHubManifestPolicy.parse("""{"schema_version":2,"items":[]}""")
        }
    }

    @Test
    fun rejectsPathTraversalAndPathsOutsideLibrary() {
        assertThrows(IllegalArgumentException::class.java) {
            GitHubManifestPolicy.parse(manifest("[${item(path = "content/library/../private.json")}]"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GitHubManifestPolicy.parse(manifest("[${item(path = "docs/lesson.json")}]"))
        }
    }

    @Test
    fun rejectsInvalidHashAndMismatchedFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            GitHubManifestPolicy.parse(manifest("[${item(hash = "not-a-hash")}]"))
        }
        val mismatched = item().replace("\"format\": \"json\"", "\"format\": \"md\"")
        assertThrows(IllegalArgumentException::class.java) {
            GitHubManifestPolicy.parse(manifest("[$mismatched]"))
        }
    }

    @Test
    fun rejectsDuplicateIdsAndPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            GitHubManifestPolicy.parse(manifest("[${item()}, ${item(path = "content/library/other.json")}]"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GitHubManifestPolicy.parse(manifest("[${item()}, ${item(id = "other")}]"))
        }
    }
}
