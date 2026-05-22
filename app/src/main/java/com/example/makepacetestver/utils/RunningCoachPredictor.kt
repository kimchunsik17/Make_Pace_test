package com.example.makepacetestver.utils

import android.content.Context
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.*

class RunningCoachPredictor(context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    private val windowBuffer: Queue<Pair<Float, Float>> = LinkedList()
    private val WINDOW_SIZE = 12

    private val PACE_MIN = 2.0f
    private val PACE_MAX = 20.0f
    private val GRADE_MIN = -0.3f
    private val GRADE_MAX = 0.3f
    private val AGE_MIN = 10.0f
    private val AGE_MAX = 80.0f
    private val WEIGHT_MIN = 40.0f
    private val WEIGHT_MAX = 120.0f

    init {
        // 1. 모델 파일과 데이터 파일을 기기 내부 저장소로 복사
        val modelPath = copyAssetToInternalStorage(context, "running_coach.onnx")
        copyAssetToInternalStorage(context, "running_coach.onnx.data") // 데이터 파일도 함께 복사

        // 2. 바이트 배열이 아닌 '파일 경로'를 전달하여 세션 생성 (이렇게 해야 연관 파일을 찾음)
        session = env.createSession(modelPath, OrtSession.SessionOptions())
    }

    private fun copyAssetToInternalStorage(context: Context, fileName: String): String {
        val file = File(context.filesDir, fileName)
        // 매번 복사할 필요 없이 없을 때만 복사 (성능 최적화)
        if (!file.exists()) {
            context.assets.open(fileName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    fun predictNextPace(
        currentPace: Float,
        currentGrade: Float,
        age: Float,
        gender: Float,
        weight: Float
    ): Float? {
        windowBuffer.add(Pair(currentPace, currentGrade))
        if (windowBuffer.size > WINDOW_SIZE) {
            windowBuffer.poll()
        }

        if (windowBuffer.size < WINDOW_SIZE) return null

        val tsInputArray = FloatArray(WINDOW_SIZE * 2)
        windowBuffer.forEachIndexed { index, pair ->
            tsInputArray[index * 2] = scale(pair.first, PACE_MIN, PACE_MAX)
            tsInputArray[index * 2 + 1] = scale(pair.second, GRADE_MIN, GRADE_MAX)
        }
        val tsInputBuffer = FloatBuffer.wrap(tsInputArray)
        val tsInputTensor = OnnxTensor.createTensor(env, tsInputBuffer, longArrayOf(1, 12, 2))

        val staticInputArray = floatArrayOf(
            scale(age, AGE_MIN, AGE_MAX),
            gender,
            scale(weight, WEIGHT_MIN, WEIGHT_MAX)
        )
        val staticInputBuffer = FloatBuffer.wrap(staticInputArray)
        val staticInputTensor = OnnxTensor.createTensor(env, staticInputBuffer, longArrayOf(1, 3))

        val inputs = mapOf(
            "ts_input" to tsInputTensor,
            "static_input" to staticInputTensor
        )

        return try {
            val results = session.run(inputs)
            val outputTensor = results[0] as OnnxTensor
            val outputArray = outputTensor.floatBuffer.array()
            inverseScale(outputArray[0], PACE_MIN, PACE_MAX)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tsInputTensor.close()
            staticInputTensor.close()
        }
    }

    private fun scale(value: Float, min: Float, max: Float): Float {
        return (value.coerceIn(min, max) - min) / (max - min)
    }

    private fun inverseScale(scaledValue: Float, min: Float, max: Float): Float {
        return scaledValue * (max - min) + min
    }

    fun close() {
        session.close()
        env.close()
    }
}
