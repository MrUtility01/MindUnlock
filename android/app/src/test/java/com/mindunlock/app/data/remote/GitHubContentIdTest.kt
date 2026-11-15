package com.mindunlock.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubContentIdTest {
    @Test
    fun importedChapterIdsUseAnIsolatedStableNamespace() {
        assertEquals("github_network-basics", githubContentChapterId("network-basics"))
        assertEquals("github_it-network-foundations", githubContentChapterId("it-network-foundations"))
    }
}
