package dev.sk2andy.materialbrowser.data.passwords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BlackHolePasswordCsvTest {
    private fun import(text: String) = BlackHolePasswordCsv.parse(
        ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),
    )

    @Test
    fun googleFormatParsesQuotedCommasMultilineAndPreservesPasswordWhitespace() {
        val preview = import(
            "\uFEFFname,url,username,password,note\r\n" +
                "\"Site, teste\",https://example.org/login,conta@example.org,\" a,\"\"b\"\"  \",\"linha 1\nlinha 2\"\r\n",
        )
        assertEquals(1, preview.totalRows)
        assertEquals(1, preview.entries.size)
        assertEquals("Site, teste", preview.entries[0].name)
        assertEquals(" a,\"b\"  ", preview.entries[0].password)
    }

    @Test
    fun preservesAndroidAppEntriesButCountsThemSeparately() {
        val preview = import(
            "name,url,username,password\n" +
                "App,android://abc@com.example.app,user,pass\n" +
                "Web,https://example.org,user,pass2\n" +
                "Bad,javascript:alert(1),user,pass3\n",
        )
        assertEquals(3, preview.totalRows)
        assertEquals(2, preview.entries.size)
        assertEquals(1, preview.invalidRows)
        assertEquals(1, preview.appOnlyRows)
    }

    @Test
    fun doesNotPrintCredentials() {
        val preview = import("url,username,password\nhttps://example.org,example-user,secret123\n")
        assertTrue("secret123" !in preview.toString())
        assertTrue("example-user" !in preview.entries[0].toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingColumns() {
        import("website,username\nhttps://example.org,u\n")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnterminatedQuotedCell() {
        import("url,username,password\n\"https://example.org,user,pwd\n")
    }

    @Test
    fun `export round trip preserves literal formulas quotes unicode and whitespace`() {
        val text = "name,url,username,password\r\n" +
            "\"Fake, site\",https://example.org,\"=FAKE()\",\" fake,\"\"quoted\"\"\n🪐  \"\r\n" +
            "App,android://fake@com.example.app,test-user,app-test-password\r\n"
        val original = import(text)
        val output = ByteArrayOutputStream()
        BlackHolePasswordCsv.write(original.entries, output)
        val reread = BlackHolePasswordCsv.parse(ByteArrayInputStream(output.toByteArray()))
        assertEquals(2, reread.entries.size)
        for (index in original.entries.indices) {
            assertEquals(original.entries[index].name, reread.entries[index].name)
            assertEquals(original.entries[index].url, reread.entries[index].url)
            assertEquals(original.entries[index].username, reread.entries[index].username)
            assertEquals(original.entries[index].password, reread.entries[index].password)
        }
        assertEquals(1, reread.appOnlyRows)
    }

    @Test
    fun `report counts invalid rows without retaining them`() {
        val preview = import("url,username,password\n" +
            "https://example.org,test,fake\n" +
            "javascript:fake,test,fake\n" +
            "https://user@example.org,test,fake\n" +
            "https://example.org,test,\n" +
            "https://example.org,test,fake,extra\n\n")
        assertEquals(5, preview.totalRows)
        assertEquals(4, preview.invalidRows)
        assertEquals(1, preview.entries.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects ambiguous aliases`() {
        import("url,website,username,password\nhttps://example.org,https://example.com,test,fake\n")
    }

    @Test(expected = java.nio.charset.MalformedInputException::class)
    fun `rejects malformed utf8 without echoing content`() {
        BlackHolePasswordCsv.parse(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bounds cells before allocating an unbounded preview`() {
        import("url,username,password\nhttps://example.org,test," + "x".repeat(16_385))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bounds record count`() {
        import("url,username,password\n" + "https://example.org,test,fake\n".repeat(20_001))
    }

    @Test
    fun `rejects nul without trimming a space-only password`() {
        val preview = import("url,username,password\nhttps://example.org,test,  \nhttps://example.org,te\u0000st,fake\n")
        assertEquals(1, preview.invalidRows)
        assertEquals("  ", preview.entries.single().password)
    }
}
