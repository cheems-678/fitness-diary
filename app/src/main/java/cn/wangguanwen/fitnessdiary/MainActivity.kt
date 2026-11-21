package cn.wangguanwen.fitnessdiary

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import cn.wangguanwen.fitnessdiary.ui.DiaryApp

class MainActivity : ComponentActivity() {
    private val model: DiaryViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.rgb(40, 37, 32)),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.rgb(40, 37, 32)),
        )
        val playIntro = (application as FitnessDiaryApplication).shouldPlayIntro(
            restoringScreen = savedInstanceState != null,
            animationsEnabled = ValueAnimator.areAnimatorsEnabled(),
        )
        setContent { DiaryApp(model, playIntro = playIntro) }
    }
}
