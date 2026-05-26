package com.example.makepacetestver.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.example.makepacetestver.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UploadRouteBottomSheet : BottomSheetDialogFragment() {

    var onRouteUploaded: (() -> Unit)? = null

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
                        // 키보드 올라와도 바텀시트 상태 유지 → IME 연결 보존
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
        val tvCustomTags    = view.findViewById<TextView>(R.id.tvCustomTags)
        val customTags      = mutableListOf<String>()

        // 거리 필드 포커스 시 전체 선택 (post{}로 UI 스레드 다음 틱에 실행)
        etDistance.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) etDistance.post { etDistance.selectAll() }
        }

        // 커스텀 태그 추가
        btnAddCustomTag.setOnClickListener {
            val tag = etCustomTag.text.toString().trim()
            if (tag.isEmpty()) {
                Toast.makeText(context, "태그를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (tag.length > 5) {
                Toast.makeText(context, "5글자 이내로 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (customTags.contains(tag)) {
                Toast.makeText(context, "이미 추가된 태그예요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            customTags.add(tag)
            etCustomTag.text.clear()
            tvCustomTags.visibility = View.VISIBLE
            tvCustomTags.text = "추가됨: " + customTags.joinToString("  ") { "#$it" }
        }

        btnSubmit.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val distanceStr = etDistance.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(context, "루트 제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (distanceStr.isEmpty()) {
                Toast.makeText(context, "거리를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val distance = distanceStr.toDoubleOrNull() ?: run {
                Toast.makeText(context, "거리를 숫자로 입력해주세요 (예: 5.2)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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
            val user = auth.currentUser ?: run {
                Toast.makeText(context, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val route = hashMapOf(
                "title"            to title,
                "distanceKm"       to distance,
                "crowdingLevel"    to crowding,
                "recommendedTimes" to times,
                "tags"             to tags,
                "pros"             to etPros.text.toString().trim(),
                "cons"             to etCons.text.toString().trim(),
                "likeCount"        to 0,
                "likedBy"          to emptyList<String>(),
                "authorId"         to user.uid,
                "authorName"       to (user.displayName ?: "익명 러너"),
                "createdAt"        to System.currentTimeMillis()
            )

            btnSubmit.isEnabled = false
            btnSubmit.text = "공유 중..."

            FirebaseFirestore.getInstance()
                .collection("running_routes")
                .add(route)
                .addOnSuccessListener {
                    Toast.makeText(context, "루트가 공유되었습니다! 🎉", Toast.LENGTH_SHORT).show()
                    onRouteUploaded?.invoke()
                    dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "루트 공유하기 🏃"
                }
        }
    }
}
