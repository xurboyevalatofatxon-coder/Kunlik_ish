package uz.dailygoals

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

/** Device/emulator smoke coverage. Not executed by the offline core runner. */
@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun resetData() {
        val graph=(compose.activity.application as DailyGoalsApp).graph
        runBlocking { graph.repository.deleteAll();graph.settings.reset() }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Yangi maqsad").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun firstLaunchShowsEmptyStateAndFiveTabs() {
        compose.onNodeWithText("Hozircha maqsadlar yo‘q").assertIsDisplayed()
        listOf("Maqsadlar","Statistika","Arxiv","Sozlamalar").forEach { compose.onAllNodesWithText(it).onLast().assertIsDisplayed() }
    }
    @Test fun createGoalAndOpenDetails() {
        compose.onNodeWithContentDescription("Yangi maqsad").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Kitob o‘qish")
        compose.onNodeWithText("Saqlash").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Maqsadlar").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Maqsadlar").onLast().performClick()
        compose.onNodeWithText("Kitob o‘qish").performClick()
        compose.onNodeWithText("Tahrirlash").assertIsDisplayed()
        compose.onNodeWithText("O‘chirish").assertIsDisplayed()
    }
    @Test fun emptyNameSaveDisabled() {
        compose.onNodeWithContentDescription("Yangi maqsad").performClick()
        compose.onNodeWithText("Saqlash").assertIsNotEnabled()
    }
    @Test fun archiveHasIndependentEmptyState() {
        compose.onNodeWithText("Arxiv").performClick()
        compose.onNodeWithText("Arxiv hozircha bo‘sh").assertIsDisplayed()
    }
    @Test fun settingsExposeExplicitExportImportAndPin() {
        compose.onNodeWithText("Sozlamalar").performClick()
        compose.onNodeWithText("PIN o‘rnatish").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Eksport").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Import").performScrollTo().assertIsDisplayed()
    }
}
