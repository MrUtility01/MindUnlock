package com.mindunlock.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubContentSyncStatusTest {
    @Test
    fun remoteUpdateSupportsEitherExplicitChoice() {
        assertTrue(GitHubContentSyncStatus.REMOTE_UPDATE.canKeepLocal)
        assertTrue(GitHubContentSyncStatus.REMOTE_UPDATE.canTakeRemote)
    }

    @Test
    fun localOnlyChangeCanOnlyBeKeptLocally() {
        assertTrue(GitHubContentSyncStatus.LOCAL_MODIFIED.canKeepLocal)
        assertFalse(GitHubContentSyncStatus.LOCAL_MODIFIED.canTakeRemote)
    }

    @Test
    fun twoSidedConflictSupportsBothExplicitChoices() {
        assertTrue(GitHubContentSyncStatus.CONFLICT.canKeepLocal)
        assertTrue(GitHubContentSyncStatus.CONFLICT.canTakeRemote)
    }

    @Test
    fun untrackedLocalContentCanBeResolvedExplicitly() {
        assertTrue(GitHubContentSyncStatus.UNTRACKED_LOCAL.canKeepLocal)
        assertTrue(GitHubContentSyncStatus.UNTRACKED_LOCAL.canTakeRemote)
    }

    @Test
    fun newAndUpToDateContentDoNotShowConflictActions() {
        listOf(
            GitHubContentSyncStatus.NEW,
            GitHubContentSyncStatus.UP_TO_DATE
        ).forEach { status ->
            assertFalse(status.canKeepLocal)
            assertFalse(status.canTakeRemote)
        }
    }
}
