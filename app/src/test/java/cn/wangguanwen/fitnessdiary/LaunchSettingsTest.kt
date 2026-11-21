package cn.wangguanwen.fitnessdiary

import android.animation.ValueAnimator
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun disabledSystemAnimationsOpenDiaryDirectly() {
        fun setDurationScale(scale: Float) {
            ReflectionHelpers.callStaticMethod<Void>(
                ValueAnimator::class.java,
                "setDurationScale",
                ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType!!, scale),
            )
        }
        val previousScale = ValueAnimator.getDurationScale()
        setDurationScale(0f)
        try {
            assertFalse(ValueAnimator.areAnimatorsEnabled())
            compose.mainClock.autoAdvance = false
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithText("健身日记").assertIsDisplayed()
                compose.onNodeWithTag("launch-intro").assertDoesNotExist()
            }
        } finally {
            setDurationScale(previousScale)
        }
    }
}
