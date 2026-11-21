package cn.wangguanwen.fitnessdiary

import android.app.Application
import androidx.annotation.MainThread

class FitnessDiaryApplication : Application() {
    private var hasOpenedDiary = false

    @MainThread
    internal fun shouldPlayIntro(restoringScreen: Boolean, animationsEnabled: Boolean): Boolean {
        val play = !hasOpenedDiary && !restoringScreen && animationsEnabled
        hasOpenedDiary = true
        return play
    }
}
