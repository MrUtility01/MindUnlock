package com.mindunlock.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubCourseGroupingTest {
    @Test
    fun growthPathPackagesAppearUnderOneNamedCourse() {
        assertEquals(
            "مسیر رشد",
            githubCourseDisplayGroup("technology-path-C01", "01 - مبانی شبکه")
        )
        assertEquals(
            "مسیر رشد",
            githubCourseDisplayGroup("technology-path-field-appendix", "پروژه‌های میدانی")
        )
    }

    @Test
    fun unrelatedGitHubContentKeepsItsOriginalGroup() {
        assertEquals(
            "Windows Server",
            githubCourseDisplayGroup("windows-server-basics", "Windows Server")
        )
    }

    @Test
    fun missingOriginalGroupDoesNotHideGrowthPath() {
        assertEquals("مسیر رشد", githubCourseDisplayGroup("technology-path-C68", null))
    }
}
