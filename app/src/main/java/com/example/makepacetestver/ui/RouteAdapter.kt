package com.example.makepacetestver.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.makepacetestver.R
import com.example.makepacetestver.data.RunningRoute

class RouteAdapter(
    private var routes: List<RunningRoute>,
    private val currentUserId: String,
    private val onLikeClick: (RunningRoute) -> Unit,
    private val onRunRoute: (RunningRoute) -> Unit = {},
    private val onComments: (RunningRoute) -> Unit = {}
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    class RouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDistance: TextView = view.findViewById(R.id.tvDistance)
        val tvCrowding: TextView = view.findViewById(R.id.tvCrowding)
        val tvRecommendedTime: TextView = view.findViewById(R.id.tvRecommendedTime)
        val tvTags: TextView = view.findViewById(R.id.tvTags)
        val tvPros: TextView = view.findViewById(R.id.tvPros)
        val tvCons: TextView = view.findViewById(R.id.tvCons)
        val tvAuthor: TextView = view.findViewById(R.id.tvAuthor)
        val tvLikeCount: TextView = view.findViewById(R.id.tvLikeCount)
        val btnLike: ImageButton = view.findViewById(R.id.btnLike)
        val btnRunThisRoute: Button = view.findViewById(R.id.btnRunThisRoute)
        val btnComments: Button = view.findViewById(R.id.btnComments)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_card, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]

        holder.tvTitle.text = route.title
        holder.tvDistance.text = "  %.1f km  ".format(route.distanceKm)
        holder.tvLikeCount.text = "${route.likeCount}"

        // 혼잡도
        val crowdingIcon = when (route.crowdingLevel) {
            "상" -> "🔴"
            "중" -> "🟡"
            else -> "🟢"
        }
        holder.tvCrowding.text = "$crowdingIcon 혼잡도 ${route.crowdingLevel}"

        // 추천 시간대
        holder.tvRecommendedTime.text = if (route.recommendedTimes.isNotEmpty()) {
            "⏰ ${route.recommendedTimes.joinToString("·")}"
        } else ""
        holder.tvRecommendedTime.visibility =
            if (route.recommendedTimes.isNotEmpty()) View.VISIBLE else View.GONE

        // 태그
        holder.tvTags.text = if (route.tags.isNotEmpty()) {
            route.tags.joinToString("  ") { "#$it" }
        } else ""
        holder.tvTags.visibility = if (route.tags.isNotEmpty()) View.VISIBLE else View.GONE

        // 장점
        holder.tvPros.text = if (route.pros.isNotEmpty()) "✨ ${route.pros}" else ""
        holder.tvPros.visibility = if (route.pros.isNotEmpty()) View.VISIBLE else View.GONE

        // 단점
        holder.tvCons.text = if (route.cons.isNotEmpty()) "⚡ ${route.cons}" else ""
        holder.tvCons.visibility = if (route.cons.isNotEmpty()) View.VISIBLE else View.GONE

        holder.tvAuthor.text = "by ${route.authorName}"

        // 좋아요 상태
        val isLiked = route.likedBy.contains(currentUserId)
        holder.btnLike.setImageResource(
            if (isLiked) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )

        holder.btnLike.setOnClickListener { onLikeClick(route) }
        holder.btnRunThisRoute.setOnClickListener { onRunRoute(route) }
        holder.btnComments.setOnClickListener { onComments(route) }
        // GPS 경로가 없으면 달리기 버튼 비활성화
        holder.btnRunThisRoute.isEnabled = route.routePoints.isNotEmpty()
        holder.btnRunThisRoute.alpha = if (route.routePoints.isNotEmpty()) 1f else 0.4f
    }

    override fun getItemCount() = routes.size

    fun updateRoutes(newRoutes: List<RunningRoute>) {
        routes = newRoutes
        notifyDataSetChanged()
    }
}
