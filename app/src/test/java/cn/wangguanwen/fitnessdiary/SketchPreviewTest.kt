package cn.wangguanwen.fitnessdiary

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SketchPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun renderFourScreensAndVerifyDraftAndDatePicker() {
        val model = ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        runBlocking(Dispatchers.IO) {
            val weights = listOf(6640, 6605, 6612, 6585, 6562, 6570, 6545, 6530, 6538, 6514, 6525)
            weights.forEachIndexed { index, weight -> model.repository.save(LocalDate.now().minusDays((weights.lastIndex - index).toLong()).toString(), weight,
                if (index == weights.lastIndex) "示例记录：今天练得很认真。\n慢慢记录，慢慢看见自己的变化。" else "示例记录：保持自己的节奏。", emptyList()) }
        }
        compose.waitUntil(15_000) { shadowOf(Looper.getMainLooper()).idle(); model.records.size == 11 && compose.onAllNodesWithContentDescription("记今天").fetchSemanticsNodes().isNotEmpty() }
        settle()
        compose.onNodeWithContentDescription("体重变化曲线：", substring = true).assertExists()
        capture("趋势")
        compose.onNodeWithText("日记").performClick(); settle(); capture("日记")
        compose.onNodeWithContentDescription("记今天").performClick(); settle(); capture("编辑")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(" 继续坚持。")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("继续编辑").performClick(); settle()
        compose.onAllNodes(hasSetTextAction())[1].assertTextContains("继续坚持。", substring = true)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃修改").performClick(); settle()
        compose.onNodeWithText("设置").performClick(); settle(); capture("设置")
        compose.onNodeWithText("导出完整备份").assertIsDisplayed()
        compose.onNodeWithText("日记").performClick(); settle()
        compose.onNodeWithContentDescription("选择日期补记").performClick()
        compose.onNodeWithText("补记或查看过去的记录").assertExists()
        compose.onNodeWithText("记录这一天").performClick(); settle()
        compose.onAllNodes(hasSetTextAction())[0].assertTextContains("65.25")
    }

    private fun settle() { compose.mainClock.advanceTimeBy(600); shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle() }

    private fun capture(name: String) {
        val output = File(System.getProperty("fitness.preview.dir"), "$name.png")
        output.parentFile!!.mkdirs()
        val bitmap = compose.runOnIdle {
            val root = compose.activity.window.decorView
            check(root.width > 0 && root.height > 0)
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        }
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        check(output.length() > 10_000) { "界面截图为空" }
    }
}
