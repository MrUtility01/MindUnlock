package com.mindunlock.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubPathPolicyTest {
    @Test
    fun normalizesRelativeRepositoryPaths() {
        assertEquals("docs/guide fa.md", GitHubPathPolicy.normalize("/docs/guide fa.md/"))
    }

    @Test
    fun encodesEachPathSegmentWithoutEncodingSeparators() {
        assertEquals("docs/guide%20fa.md", GitHubPathPolicy.encodePath("docs/guide fa.md"))
    }

    @Test
    fun rejectsTraversalAndEmptySegments() {
        assertThrows(IllegalArgumentException::class.java) {
            GitHubPathPolicy.normalize("docs/../secrets.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GitHubPathPolicy.normalize("docs//file.md")
        }
    }

    @Test
    fun rejectsControlCharactersAndQueryInjection() {
        assertThrows(IllegalArgumentException::class.java) {
            GitHubPathPolicy.normalize("docs/file.md?ref=other")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GitHubPathPolicy.normalize("docs/hidden\u0001.md")
        }
    }
}
