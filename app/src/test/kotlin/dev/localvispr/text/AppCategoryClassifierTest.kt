package dev.localvispr.text

import kotlin.test.assertEquals
import org.junit.Test

class AppCategoryClassifierTest {

    @Test
    fun classifiesMessengersAsPersonal() {
        assertEquals(StyleCategory.PERSONAL, StyleCategory.categoryForPackage("com.whatsapp"))
        assertEquals(
            StyleCategory.PERSONAL,
            StyleCategory.categoryForPackage("org.telegram.messenger"),
        )
        assertEquals(
            StyleCategory.PERSONAL,
            StyleCategory.categoryForPackage("org.thoughtcrime.securesms"),
        )
        assertEquals(
            StyleCategory.PERSONAL,
            StyleCategory.categoryForPackage("com.google.android.apps.messaging"),
        )
    }

    @Test
    fun classifiesBusinessToolsAsWork() {
        assertEquals(StyleCategory.WORK, StyleCategory.categoryForPackage("com.Slack"))
        assertEquals(StyleCategory.WORK, StyleCategory.categoryForPackage("com.microsoft.teams"))
        assertEquals(StyleCategory.WORK, StyleCategory.categoryForPackage("notion.id"))
    }

    @Test
    fun classifiesEmailClientsAsEmail() {
        assertEquals(StyleCategory.EMAIL, StyleCategory.categoryForPackage("com.google.android.gm"))
        assertEquals(
            StyleCategory.EMAIL,
            StyleCategory.categoryForPackage("com.microsoft.office.outlook"),
        )
    }

    @Test
    fun fallsBackToOtherForUnknownAppsOrNull() {
        assertEquals(StyleCategory.OTHER, StyleCategory.categoryForPackage(null))
        assertEquals(StyleCategory.OTHER, StyleCategory.categoryForPackage("com.android.chrome"))
        assertEquals(
            StyleCategory.OTHER,
            StyleCategory.categoryForPackage("com.google.android.keep"),
        )
    }
}
