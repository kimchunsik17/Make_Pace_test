package com.example.makepacetestver.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.makepacetestver.R
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.data.db.RunPointEntity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class ShareToClubBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_RUN_ID       = "run_id"
        private const val ARG_DISTANCE_KM  = "distance_km"
        private const val ARG_AVG_PACE     = "avg_pace"
        private const val ARG_DURATION     = "duration_millis"

        fun newInstance(runId: Long, distanceKm: Double, avgPace: String, durationMillis: Long): ShareToClubBottomSheet {
            return ShareToClubBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_RUN_ID, runId)
                    putDouble(ARG_DISTANCE_KM, distanceKm)
                    putString(ARG_AVG_PACE, avgPace)
                    putLong(ARG_DURATION, durationMillis)
                }
            }
        }
    }

    var onShared: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).also { dialog ->
            dialog.setOnShowListener {
                val bs = dialog.findViewById<android.widget.FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
                bs?.let {
                    BottomSheetBehavior.from(it).apply {
                        state = BottomSheetBehavior.STATE_EXPANDED
                        skipCollapsed = true
                        isHideable = false
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_upload_route, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 한글 IME + 키보드 레이아웃 조정
        dialog?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        val runId        = arguments?.getLong(ARG_RUN_ID) ?: -1L
        val distanceKm   = arguments?.getDouble(ARG_DISTANCE_KM) ?: 0.0
        val avgPace      = arguments?.getString(ARG_AVG_PACE) ?: ""
        val durationMs   = arguments?.getLong(ARG_DURATION) ?: 0L

        val etTitle         = view.findViewById<EditText>(R.id.etRouteTitle)
        val etDistance      = view.findViewById<EditText>(R.id.etRouteDistance)
        val rgCrowding      = view.findViewById<RadioGroup>(R.id.rgCrowding)
        val cbTimeDawn      = view.findViewById<CheckBox>(R.id.cbTimeDawn)
        val cbTimeMorning   = view.findViewById<CheckBox>(R.id.cbTimeMorning)
        val cbTimeAfternoon = view.findViewById<CheckBox>(R.id.cbTimeAfternoon)
        val cbTimeEvening   = view.findViewById<CheckBox>(R.id.cbTimeEvening)
        val cbTimeNight     = view.findViewById<CheckBox>(R.id.cbTimeNight)
        val cbTagSea        = view.findViewById<CheckBox>(R.id.cbTagSea)
        val cbTagMountain   = view.findViewById<CheckBox>(R.id.cbTagMountain)
        val cbTagPark       = view.findViewById<CheckBox>(R.id.cbTagPark)
        val cbTagRiver      = view.findViewById<CheckBox>(R.id.cbTagRiver)
        val cbTagCity       = view.findViewById<CheckBox>(R.id.cbTagCity)
        val cbTagNightView  = view.findViewById<CheckBox>(R.id.cbTagNightView)
        val etPros          = view.findViewById<EditText>(R.id.etPros)
        val etCons          = view.findViewById<EditText>(R.id.etCons)
        val btnSubmit       = view.findViewById<Button>(R.id.btnSubmitRoute)

        // 커스텀 태그
        val etCustomTag     = view.findViewById<EditText>(R.id.etCustomTag)
        val btnAddCustomTag = view.findViewById<Button>(R.id.btnAddCustomTag)
        val tvCustomTags    = view.findViewById<android.widget.TextView>(R.id.tvCustomTags)
        val customTags      = mutableListOf<String>()

        // 이번 러닝 기록으로 거리 자동 입력 + 포커스 시 전체 선택
        etDistance.setText(String.format("%.2f", distanceKm))
        etDistance.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) etDistance.post { etDistance.selectAll() }
        }

        // 커스텀 태그 추가
        btnAddCustomTag.setOnClickListener {
            val tag = etCustomTag.text.toString().trim()
            if (tag.isEmpty()) return@setOnClickListener
            if (tag.length > 5) {
                android.widget.Toast.makeText(context, "5글자 이내로 입력해주세요", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!customTags.contains(tag)) {
                customTags.add(tag)
                etCustomTag.text.clear()
                tvCustomTags.visibility = View.VISIBLE
                tvCustomTags.text = "추가됨: " + customTags.joinToString("  ") { "#$it" }
            }
        }

        btnSubmit.setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(context, "루트 제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val distance = etDistance.text.toString().toDoubleOrNull() ?: distanceKm
            val crowding = when (rgCrowding.checkedRadioButtonId) {
                R.id.rbCrowdingLow  -> "하"
                R.id.rbCrowdingHigh -> "상"
                else                -> "중"
            }
            val times = buildList {
                if (cbTimeDawn.isChecked)      add("새벽")
                if (cbTimeMorning.isChecked)   add("오전")
                if (cbTimeAfternoon.isChecked) add("오후")
                if (cbTimeEvening.isChecked)   add("저녁")
                if (cbTimeNight.isChecked)     add("야간")
            }
            val tags = buildList {
                if (cbTagSea.isChecked)       add("바닷길")
                if (cbTagMountain.isChecked)  add("산길")
                if (cbTagPark.isChecked)      add("공원")
                if (cbTagRiver.isChecked)     add("강변")
                if (cbTagCity.isChecked)      add("도심")
                if (cbTagNightView.isChecked) add("야경")
                addAll(customTags)
            }

            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: return@setOnClickListener

            btnSubmit.isEnabled = false
            btnSubmit.text = "경로 불러오는 중..."

            // Room DB에서 GPS 포인트 로드 → Firestore에 업로드
            lifecycleScope.launch {
                val points = if (runId != -1L) {
                    AppDatabase.getDatabase(requireContext()).getRunDao().getPointsForRun(runId)
                } else emptyList()

                // 경로 다운샘플링 (최대 200포인트로 제한)
                val sampled = downsample(points, 200)
                val routePoints = sampled.map { mapOf("lat" to it.latitude, "lng" to it.longitude) }

                val route = hashMapOf(
                    "title"            to title,
                    "distanceKm"       to distance,
                    "avgPace"          to avgPace,
                    "durationMillis"   to durationMs,
                    "crowdingLevel"    to crowding,
                    "recommendedTimes" to times,
                    "tags"             to tags,
                    "pros"             to etPros.text.toString().trim(),
                    "cons"             to etCons.text.toString().trim(),
                    "likeCount"        to 0,
                    "likedBy"          to emptyList<String>(),
                    "authorId"         to user.uid,
                    "authorName"       to (user.displayName ?: "익명 러너"),
                    "createdAt"        to System.currentTimeMillis(),
                    "routePoints"      to routePoints
                )

                FirebaseFirestore.getInstance()
                    .collection("running_routes")
                    .add(route)
                    .addOnSuccessListener {
                        Toast.makeText(context, "루트가 클럽에 공유되었습니다! 🎉", Toast.LENGTH_SHORT).show()
                        onShared?.invoke()
                        dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "공유 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnSubmit.isEnabled = true
                        btnSubmit.text = "루트 공유하기 🏃"
                    }
            }
        }
    }

    private fun downsample(points: List<RunPointEntity>, maxCount: Int): List<RunPointEntity> {
        if (points.size <= maxCount) return points
        val step = points.size / maxCount
        return points.filterIndexed { index, _ -> index % step == 0 }
    }
}
