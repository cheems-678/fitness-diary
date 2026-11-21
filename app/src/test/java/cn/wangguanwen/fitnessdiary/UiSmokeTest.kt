package cn.wangguanwen.fitnessdiary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun appOpensAndDailyRecordFlowsAcrossThreeScreens() {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("launch-intro").assertIsDisplayed().performTouchInput { click() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("launch-intro").assertDoesNotExist()
        compose.mainClock.autoAdvance = true

        try {
            compose.waitUntil(15_000) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.onAllNodesWithContentDescription("记今天").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            val model = ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
            throw AssertionError("loaded=${model.loaded}, loadError=${model.loadError}; UI: ${compose.onRoot().printToString()}", error)
        }
        compose.onNodeWithContentDescription("记今天").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("65.25")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("完成一次训练，状态不错。")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(15_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithContentDescription("记今天").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("65.25").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("日记").performClick()
        compose.onNodeWithText("完成一次训练，状态不错。").assertIsDisplayed()
        compose.onNodeWithText("65.25 kg").assertIsDisplayed()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("导出完整备份").assertIsDisplayed()
        compose.onNodeWithText("从备份恢复").assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("导出完整备份").assertIsDisplayed()
        compose.onNodeWithTag("launch-intro").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("launch-intro").assertDoesNotExist()
        }
    }
}
