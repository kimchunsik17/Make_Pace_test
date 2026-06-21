package com.example.makepacetestver.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.makepacetestver.R
import com.example.makepacetestver.data.RunningRoute
import com.example.makepacetestver.data.db.AppDatabase
import com.example.makepacetestver.databinding.FragmentClubBinding
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ClubFragment : Fragment() {

    private var _binding: FragmentClubBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RouteAdapter
    private lateinit var viewModel: RunningViewModel
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var allRoutes = listOf<RunningRoute>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val runDao = AppDatabase.getDatabase(requireContext()).getRunDao()
        val factory = RunningViewModelFactory(runDao)
        viewModel = ViewModelProvider(requireActivity(), factory)[RunningViewModel::class.java]

        val currentUserId = auth.currentUser?.uid ?: ""

        adapter = RouteAdapter(
            routes = emptyList(),
            currentUserId = currentUserId,
            onLikeClick = { route -> toggleLike(route) },
            onRunRoute = { route -> showPaceInputDialog(route) },
            onComments = { route ->
                RouteCommentsBottomSheet.newInstance(route.id, route.title)
                    .show(parentFragmentManager, "route_comments")
            }
        )

        binding.rvRoutes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutes.adapter = adapter

        loadRoutes()

        // 거리 검색
        binding.btnSearch.setOnClickListener {
            val km = binding.etDistanceFilter.text.toString().toDoubleOrNull()
            if (km == null) {
                Toast.makeText(context, "km를 숫자로 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            filterByDistance(km)
        }

        // 전체 보기
        binding.btnShowAll.setOnClickListener {
            binding.etDistanceFilter.text.clear()
            showRoutes(allRoutes)
        }

        // 루트 업로드 (수동 입력 - GPS 기록 없이)
        binding.fabUpload.setOnClickListener {
            val sheet = UploadRouteBottomSheet()
            sheet.onRouteUploaded = { loadRoutes() }
            sheet.show(parentFragmentManager, "upload_route")
        }
    }

    private fun showPaceInputDialog(route: RunningRoute) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_pace_input, null)
        val etPace   = dialogView.findViewById<EditText>(R.id.etPaceInput)
        val tvError  = dialogView.findViewById<TextView>(R.id.tvPaceError)
        val tvLabel  = dialogView.findViewById<TextView>(R.id.tvPaceRouteLabel)

        tvLabel.text = "🏃 ${route.title}"
        etPace.setText(route.avgPace.ifEmpty { "5:30" })
        etPace.setSelection(etPace.text.length)

        // ; → : 자동 수정 (오타 방지)
        etPace.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                val str = s?.toString() ?: return
                if (str.contains(';')) {
                    isFormatting = true
                    val fixed = str.replace(';', ':')
                    s.replace(0, s.length, fixed)
                    isFormatting = false
                }
            }
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("달리기 시작", null) // null: 버튼 클릭 시 자동 닫힘 방지
            .setNegativeButton("취소", null)
            .create()

        dialog.setOnShowListener {
            etPace.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val paceStr = etPace.text.toString().trim()
                val targetSec = parsePaceToSeconds(paceStr)
                if (targetSec <= 0) {
                    tvError.text = "⚠ 형식이 올바르지 않아요  (예: 5:30)"
                    tvError.visibility = View.VISIBLE
                    etPace.selectAll()
                    return@setOnClickListener
                }
                tvError.visibility = View.GONE

                val latLngPoints = route.routePoints.mapNotNull { point ->
                    val lat = point["lat"] ?: return@mapNotNull null
                    val lng = point["lng"] ?: return@mapNotNull null
                    LatLng(lat, lng)
                }

                viewModel.setClubRoute(latLngPoints, targetSec, route.id)
                dialog.dismiss()
                (requireActivity() as? com.example.makepacetestver.MainActivity)?.selectBottomNavTab(R.id.nav_run)
                Toast.makeText(context, "참조 경로가 지도에 표시됩니다!", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun parsePaceToSeconds(paceStr: String): Int {
        return try {
            // 세미콜론(;) 오타도 허용
            val normalized = paceStr.replace(';', ':')
            val parts = normalized.split(":")
            if (parts.size == 2) {
                val min = parts[0].trim().toInt()
                val sec = parts[1].trim().toInt()
                if (min in 1..20 && sec in 0..59) min * 60 + sec else 0
            } else 0
        } catch (e: NumberFormatException) { 0 }
    }

    private fun loadRoutes() {
        db.collection("running_routes")
            .orderBy("likeCount", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                allRoutes = docs.map { doc ->
                    // routePoints: List<Map<String, Any>> → List<Map<String, Double>>
                    @Suppress("UNCHECKED_CAST")
                    val rawPoints = doc.get("routePoints") as? List<Map<String, Any>> ?: emptyList()
                    val routePoints = rawPoints.map { pt ->
                        mapOf(
                            "lat" to (pt["lat"] as? Double ?: (pt["lat"] as? Number)?.toDouble() ?: 0.0),
                            "lng" to (pt["lng"] as? Double ?: (pt["lng"] as? Number)?.toDouble() ?: 0.0)
                        )
                    }
                    RunningRoute(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        pros = doc.getString("pros") ?: "",
                        cons = doc.getString("cons") ?: "",
                        distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                        avgPace = doc.getString("avgPace") ?: "",
                        durationMillis = doc.getLong("durationMillis") ?: 0L,
                        crowdingLevel = doc.getString("crowdingLevel") ?: "중",
                        recommendedTimes = (doc.get("recommendedTimes") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList(),
                        tags = (doc.get("tags") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList(),
                        likeCount = doc.getLong("likeCount")?.toInt() ?: 0,
                        likedBy = (doc.get("likedBy") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList(),
                        authorId = doc.getString("authorId") ?: "",
                        authorName = doc.getString("authorName") ?: "익명",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        routePoints = routePoints
                    )
                }
                showRoutes(allRoutes)
            }
            .addOnFailureListener {
                Toast.makeText(context, "루트를 불러오지 못했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterByDistance(targetKm: Double) {
        val margin = 1.0 // ±1km 범위
        val filtered = allRoutes.filter { route ->
            route.distanceKm >= (targetKm - margin) && route.distanceKm <= (targetKm + margin)
        }
        showRoutes(filtered)
        if (filtered.isEmpty()) {
            Toast.makeText(context, "${targetKm}km 근처 루트가 없어요. 전체 버튼을 눌러보세요!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRoutes(routes: List<RunningRoute>) {
        adapter.updateRoutes(routes)
        binding.tvRouteCount.text = "루트 ${routes.size}개"
    }

    private fun toggleLike(route: RunningRoute) {
        val userId = auth.currentUser?.uid ?: return
        val routeRef = db.collection("running_routes").document(route.id)

        db.runTransaction { transaction ->
            // 트랜잭션 안에서 최신 상태를 읽어 판단 (카운트-명단 동기화 보장 + 충돌 시 자동 재시도)
            val snapshot = transaction.get(routeRef)
            val likedBy = (snapshot.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val currentCount = snapshot.getLong("likeCount") ?: 0L

            if (likedBy.contains(userId)) {
                transaction.update(routeRef, "likedBy", FieldValue.arrayRemove(userId))
                transaction.update(routeRef, "likeCount", (currentCount - 1).coerceAtLeast(0))
            } else {
                transaction.update(routeRef, "likedBy", FieldValue.arrayUnion(userId))
                transaction.update(routeRef, "likeCount", currentCount + 1)
            }
        }.addOnSuccessListener {
            loadRoutes()
        }.addOnFailureListener {
            Toast.makeText(context, "좋아요 처리 실패", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
