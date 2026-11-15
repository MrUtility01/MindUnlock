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
        assertEquals(true, parsed.any { it.id == "network-basics" && it.path == "content/library/network-basics.json" })
    }

    @Test
    fun acceptsCompressedCourseBundleManifestEntry() {
        val compressed = """
            {
              "id": "technology-path-index",
              "title": "مسیر پیشرفت فناوری",
              "group_name": "مسیر پیشرفت فناوری",
              "order_num": 0,
              "path": "content/library/technology-path/complete-course.b64",
              "format": "json-gzip-base64",
              "content_hash": "${"a".repeat(64)}",
              "updated_at": "2026-11-15T00:00:00Z",
              "schema_version": 1
            }
        """.trimIndent()
        val parsed = GitHubManifestPolicy.parse(manifest("[$compressed]"))
        assertEquals(1, parsed.size)
        assertEquals("technology-path-index", parsed.single().id)
        assertEquals("json-gzip-base64", parsed.single().format)
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
