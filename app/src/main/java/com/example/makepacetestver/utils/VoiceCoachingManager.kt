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
            // QUEUE_ADD를 사용하여 이전 음성이 끝나면 이어서 나오도록 설정 (큐잉)
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    fun coachPace(currentPaceSeconds: Int, targetPaceSeconds: Int, tolerance: Float) {
        val diff = currentPaceSeconds - targetPaceSeconds
        val threshold = targetPaceSeconds * tolerance

        when {
            diff > threshold -> speak("페이스를 높이세요. 조금 더 빠르게 달려야 합니다.")
            diff < -threshold -> speak("페이스를 낮추세요. 너무 빠르게 달리고 있습니다.")
            // 적정 범위 내에 있을 때는 아무 말도 하지 않음 (사용자 요청 반영)
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
