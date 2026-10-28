package dev.localvispr.text

import kotlin.test.assertEquals
import org.junit.Test

class EmailLayoutFormatterTest {

    @Test
    fun formatsGermanGreetingAndSignOffWithBlankLines() {
        val input = "Hallo Herr Schmidt, vielen Dank für die Unterlagen. Viele Grüße Markus"
        val expected =
            "Hallo Herr Schmidt,\n\nvielen Dank für die Unterlagen.\n\nViele Grüße\nMarkus"
        assertEquals(expected, EmailLayoutFormatter.format(input, Language.GERMAN))
    }

    @Test
    fun formatsFormalGermanGreetingAndMitFreundlichenGruessen() {
        val input =
            "Sehr geehrte Damen und Herren, anbei sende ich die Rechnung. Mit freundlichen Grüßen Dr. Weber"
        val expected =
            "Sehr geehrte Damen und Herren,\n\nanbei sende ich die Rechnung.\n\nMit freundlichen Grüßen\nDr. Weber"
        assertEquals(expected, EmailLayoutFormatter.format(input, Language.GERMAN))
    }

    @Test
    fun formatsEnglishEmailWithGreetingAndSignOff() {
        val input = "Hi team, please find the report attached. Best regards, Sarah"
        val expected = "Hi team,\n\nplease find the report attached.\n\nBest regards,\nSarah"
        assertEquals(expected, EmailLayoutFormatter.format(input, Language.ENGLISH))
    }

    @Test
    fun leavesTextWithoutGreetingOrSignOffUntouched() {
        val input = "Das Meeting findet morgen um 10 Uhr statt. Bitte pünktlich sein."
        assertEquals(input, EmailLayoutFormatter.format(input, Language.GERMAN))
    }

    @Test
    fun normalizesExistingLineBreaksWithoutDuplicateBlanks() {
        val input = "Hallo Anna,\n\nDanke für die Info.\nViele Grüße\nNils"
        val expected = "Hallo Anna,\n\nDanke für die Info.\n\nViele Grüße\nNils"
        assertEquals(expected, EmailLayoutFormatter.format(input, Language.GERMAN))
    }

    @Test
    fun formatsSignOffAloneWithoutInventingBodyOrGreeting() {
        val input = "Viele Grüße Markus"
        val expected = "Viele Grüße\nMarkus"
        assertEquals(expected, EmailLayoutFormatter.format(input, Language.GERMAN))
    }

    @Test
    fun formatsGreetingAloneWithoutTrailingEmptyLines() {
        val input = "Hallo zusammen,"
        assertEquals("Hallo zusammen,", EmailLayoutFormatter.format(input, Language.GERMAN))
    }
}
