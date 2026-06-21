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
        val curMin = currentPaceSeconds / 60
        val curSec = currentPaceSeconds % 60
        val tgtMin = targetPaceSeconds / 60
        val tgtSec = targetPaceSeconds % 60

        when {
            diff > threshold -> speak(
                "평균 페이스 ${curMin}분 ${curSec}초. 목표 ${tgtMin}분 ${tgtSec}초보다 느립니다. 조금 더 빠르게 달려보세요."
            )
            diff < -threshold -> speak(
                "평균 페이스 ${curMin}분 ${curSec}초. 목표보다 빠릅니다. 페이스를 조금 줄이세요."
            )
            else -> speak(
                "평균 페이스 ${curMin}분 ${curSec}초. 목표 페이스를 잘 유지하고 있습니다."
            )
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
