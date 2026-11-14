package com.mindunlock.app.data.local

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MindMapDaoTest {
    private lateinit var database: UserDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            UserDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun linkedSourceCanOnlyBeAddedOnce() = runBlocking {
        val dao = database.mindMapDao()
        val node = MindMapNodeEntity(
            title = "یادداشت شبکه",
            nodeType = "یادداشت",
            sourceType = "note",
            sourceId = 7L
        )

        val firstId = dao.createLinkedNode(node)
        val duplicateId = dao.createLinkedNode(node.copy(title = "نسخه تکراری"))

        assertTrue(firstId > 0L)
        assertEquals(-1L, duplicateId)
        assertEquals(1, dao.getAllNodes().size)
    }

    @Test
    fun connectionRejectsInvalidAndDuplicateEdges() = runBlocking {
        val dao = database.mindMapDao()
        val first = dao.insertNode(MindMapNodeEntity(title = "مبدأ"))
        val second = dao.insertNode(MindMapNodeEntity(title = "مقصد"))

        assertFalse(dao.connect(first, first, "پیش‌نیاز"))
        assertFalse(dao.connect(first, 999L, "پیش‌نیاز"))
        assertTrue(dao.connect(first, second, "پیش‌نیاز"))
        assertFalse(dao.connect(first, second, "پیش‌نیاز"))
        assertEquals(1, dao.getAllEdges().size)
    }

    @Test
    fun deletingNodeCascadesItsRelationships() = runBlocking {
        val dao = database.mindMapDao()
        val first = dao.insertNode(MindMapNodeEntity(title = "گره اول"))
        val second = dao.insertNode(MindMapNodeEntity(title = "گره دوم"))
        assertTrue(dao.connect(first, second, "مرتبط با"))

        dao.deleteNode(first)

        assertTrue(dao.getAllNodes().none { it.id == first })
        assertTrue(dao.getAllEdges().isEmpty())
    }
}
