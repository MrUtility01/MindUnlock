package com.mindunlock.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubContentIdTest {
    @Test
    fun importedChapterIdsUseAnIsolatedStableNamespace() {
        assertEquals("github_network-basics", githubContentChapterId("network-basics"))
        assertEquals("github_it-network-foundations", githubContentChapterId("it-network-foundations"))
    }
    @Test
    fun technologyPathBundleChaptersUseDedicatedFingerprintNamespace() {
        assertEquals(
            "github_technology-path-C01",
            githubContentChapterId("technology-path-C01")
        )
        assertEquals(
            "github_technology-path-X03",
            githubContentChapterId("technology-path-X03")
        )
    }

}
