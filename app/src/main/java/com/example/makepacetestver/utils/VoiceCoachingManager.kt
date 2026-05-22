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
            diff > threshold -> speak("페이스를 높이세요. 조금 더 빠르게 달려야 합니다.")
            diff < -threshold -> speak("페이스를 낮추세요. 너무 빠르게 달리고 있습니다.")
            else -> speak("좋습니다. 현재 페이스를 잘 유지하세요.")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
