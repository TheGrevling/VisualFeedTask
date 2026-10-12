package com.example.visualfeedtask.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HackerNewsItemDtoTest {
    private val gson = Gson()

    @Test
    fun `maps hacker news field names`() {
        val item = gson.fromJson(
            """{"id":7,"type":"story","title":"News","by":"alice","score":42,"time":1234}""",
            HackerNewsItemDto::class.java,
        )
        assertEquals(7L, item.id)
        assertEquals("alice", item.author)
        assertEquals(1234L, item.submittedAtEpochSeconds)
        assertNull(item.url)
    }

    @Test
    fun `deleted item does not require story fields`() {
        val item = gson.fromJson(
            """{"id":7,"deleted":true}""",
            HackerNewsItemDto::class.java,
        )
        assertEquals(true, item.deleted)
        assertNull(item.title)
        assertNull(item.author)
        assertNull(item.submittedAtEpochSeconds)
    }
}
