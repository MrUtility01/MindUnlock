package com.mindunlock.app

import com.mindunlock.app.data.local.LaterTopicEntity
import com.mindunlock.app.data.local.MindDumpEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningPersonalTopicPolicyTest {
    @Test
    fun showsOnlyOpenLaterTopicsLinkedToLearningMindDumpEntries() {
        val entries = listOf(
            MindDumpEntity(id = 1, content = "مرور DNS", category = "learning", status = "later"),
            MindDumpEntity(id = 2, content = "خرید تجهیزات", category = "thought", status = "later"),
            MindDumpEntity(id = 3, content = "تمرین VLAN", category = "learning", status = "inbox")
        )
        val expected = LaterTopicEntity(
            id = 11,
            title = "مرور DNS",
            details = "مرور رکوردها",
            sourceMindDumpId = 1
        )
        val topics = listOf(
            expected,
            LaterTopicEntity(id = 12, title = "خرید تجهیزات", sourceMindDumpId = 2),
            LaterTopicEntity(id = 13, title = "هنوز در صندوق", sourceMindDumpId = 3),
            LaterTopicEntity(id = 14, title = "موضوع مستقل"),
            LaterTopicEntity(id = 15, title = "تمام‌شده", status = "done", sourceMindDumpId = 1),
            LaterTopicEntity(id = 16, title = "نسخه تکراری", sourceMindDumpId = 1)
        )

        assertEquals(listOf(expected), LearningPersonalTopicPolicy.topicsForLearningEntries(topics, entries))
    }

    @Test
    fun emptyInputsProduceNoPersonalLearningTopics() {
        assertEquals(
            emptyList<LaterTopicEntity>(),
            LearningPersonalTopicPolicy.topicsForLearningEntries(emptyList(), emptyList())
        )
    }
}
