package com.example.makepacetestver.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

class VoiceCoachingManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
            isReady = true
        }
    }

    fun speak(text: String) {
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun coachPace(currentPaceSeconds: Int, targetPaceSeconds: Int, tolerance: Float) {
        val diff = currentPaceSeconds - targetPaceSeconds
        val threshold = targetPaceSeconds * tolerance

        when {
            diff > threshold -> speak("페이스가 너무 쳐지고 있습니다. 조금 더 힘내세요!")
            diff < -threshold -> speak("너무 빠릅니다. 페이스를 늦춰서 안정적으로 달리세요.")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
