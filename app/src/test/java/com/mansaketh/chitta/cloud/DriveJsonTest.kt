package com.mansaketh.chitta.cloud

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drive's answers, written out by hand in the shape the v3 API documents. The network is not touched. */
class DriveJsonTest {
    @Test fun readsAPageOfFilesWithANextPageToken() {
        val body = """
            {"nextPageToken":"abc==","files":[
              {"id":"1","name":"chitta-1763100000000.db","size":"20480","modifiedTime":"2026-11-14T05:30:00.000Z"},
              {"id":"2","name":"paddhu.sqlite","size":"9000000","modifiedTime":"2026-10-01T00:00:00Z","mimeType":"application/octet-stream"}
            ]}
        """.trimIndent()
        val (files, next) = DriveJson.page(body)
        assertEquals("abc==", next)
        assertEquals(2, files.size)
        assertEquals("chitta-1763100000000.db", files[0].name)
        assertEquals(20480L, files[0].sizeBytes)
        assertEquals(1_794_634_200_000L, files[0].modifiedMs)
        assertEquals(9_000_000L, files[1].sizeBytes)
    }

    @Test fun anEmptyFolderIsAnEmptyList() {
        val (files, next) = DriveJson.page("""{"files":[]}""")
        assertTrue(files.isEmpty())
        assertNull(next)
        assertTrue(DriveJson.page("{}").first.isEmpty())
    }

    @Test fun aMissingSizeOrDateIsZeroNotACrash() {
        val f = DriveJson.one("""{"id":"x","name":"chitta-5.db"}""")
        assertEquals(0L, f.sizeBytes)
        assertEquals(0L, f.modifiedMs)
        assertEquals("x", f.id)
        val bad = DriveJson.one("""{"id":"y","name":"n","size":"big","modifiedTime":"yesterday"}""")
        assertEquals(0L, bad.sizeBytes)
        assertEquals(0L, bad.modifiedMs)
    }

    @Test fun anUploadAnswerIsOneFile() {
        val f = DriveJson.one("""{"id":"abc","name":"chitta-9.db","size":"123","modifiedTime":"2026-11-14T00:00:00Z"}""")
        assertEquals("abc", f.id)
        assertEquals(123L, f.sizeBytes)
    }

    @Test fun errorsAreExplainedInPlainWords() {
        assertTrue(DriveJson.explain(403, """{"error":{"errors":[{"reason":"accessNotConfigured"}]}}""").contains("switched off"))
        assertTrue(DriveJson.explain(403, """{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""").contains("full"))
        assertTrue(DriveJson.explain(403, "{}").contains("refused"))
        assertTrue(DriveJson.explain(401, "").contains("try again", ignoreCase = true))
        assertTrue(DriveJson.explain(404, "").contains("no longer"))
        assertTrue(DriveJson.explain(503, "").contains("try again later"))
        assertTrue(DriveJson.explain(429, "").contains("later"))
    }
}
