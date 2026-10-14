package com.example.visualfeedtask.ui.feed.models

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryCardTest {
    @Test
    fun `formats submission time as hours minutes then day month year`() {
        // 2026-10-14T07:36:00Z
        assertEquals("07:36 14/10/2026", formatSubmissionTime(1_791_963_360, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun `formats submission time in the given time zone`() {
        assertEquals(
            "09:36 14/10/2026",
            formatSubmissionTime(1_791_963_360, TimeZone.getTimeZone("Europe/Berlin")),
        )
    }
}
