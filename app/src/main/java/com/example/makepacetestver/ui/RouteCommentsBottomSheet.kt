package com.example.makepacetestver.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.makepacetestver.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class RouteCommentsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(routeId: String, routeTitle: String): RouteCommentsBottomSheet {
            return RouteCommentsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("routeId", routeId)
                    putString("routeTitle", routeTitle)
                }
            }
        }
    }

    data class Comment(
        val authorName: String,
        val text: String,
        val createdAt: Long,
        val isVerified: Boolean   // 실제로 뛴 사람 여부
    )

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottom_sheet_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val routeId    = arguments?.getString("routeId") ?: return
        val routeTitle = arguments?.getString("routeTitle") ?: ""

        view.findViewById<TextView>(R.id.tvCommentsTitle).text = "💬 $routeTitle"

        val rvComments  = view.findViewById<RecyclerView>(R.id.rvComments)
        val etComment   = view.findViewById<EditText>(R.id.etComment)
        val btnSubmit   = view.findViewById<Button>(R.id.btnSubmitComment)
        val tvNoRun     = view.findViewById<TextView>(R.id.tvNoRunWarning)
        val tvCommentCount = view.findViewById<TextView>(R.id.tvCommentCount)

        val db   = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val uid  = auth.currentUser?.uid ?: ""

        val adapter = CommentsAdapter()
        rvComments.layoutManager = LinearLayoutManager(requireContext())
        rvComments.adapter = adapter

        // 댓글 로드
        fun loadComments() {
            db.collection("running_routes").document(routeId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { docs ->
                    val comments = docs.map { doc ->
                        Comment(
                            authorName = doc.getString("authorName") ?: "익명",
                            text       = doc.getString("text") ?: "",
                            createdAt  = doc.getLong("createdAt") ?: 0L,
                            isVerified = doc.getBoolean("isVerified") ?: false
                        )
                    }
                    adapter.setComments(comments)
                    tvCommentCount.text = "댓글 ${comments.size}개"
                }
        }

        // 실제 뛴 사람인지 확인 → 입력창 표시 여부 결정
        db.collection("running_routes").document(routeId).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val ranBy = (doc.get("ranBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val hasRun = ranBy.contains(uid)

                if (hasRun) {
                    tvNoRun.visibility = View.GONE
                    etComment.visibility = View.VISIBLE
                    btnSubmit.visibility = View.VISIBLE
                } else {
                    tvNoRun.visibility = View.VISIBLE
                    etComment.visibility = View.GONE
                    btnSubmit.visibility = View.GONE
                }
            }

        // 댓글 제출
        btnSubmit.setOnClickListener {
            val text = etComment.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val user = auth.currentUser ?: return@setOnClickListener
            val comment = hashMapOf(
                "authorId"   to user.uid,
                "authorName" to (user.displayName ?: "익명"),
                "text"       to text,
                "createdAt"  to System.currentTimeMillis(),
                "isVerified" to true
            )

            btnSubmit.isEnabled = false
            db.collection("running_routes").document(routeId)
                .collection("comments").add(comment)
                .addOnSuccessListener {
                    etComment.text.clear()
                    btnSubmit.isEnabled = true
                    loadComments()
                }
                .addOnFailureListener {
                    btnSubmit.isEnabled = true
                    Toast.makeText(context, "댓글 등록 실패", Toast.LENGTH_SHORT).show()
                }
        }

        loadComments()
    }

    // 댓글 어댑터
    inner class CommentsAdapter : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {
        private var comments = listOf<Comment>()
        private val sdf = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault())

        fun setComments(list: List<Comment>) {
            comments = list
            notifyDataSetChanged()
        }

        inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvAuthor  : TextView = view.findViewById(R.id.tvCommentAuthor)
            val tvText    : TextView = view.findViewById(R.id.tvCommentText)
            val tvDate    : TextView = view.findViewById(R.id.tvCommentDate)
            val tvVerified: TextView = view.findViewById(R.id.tvCommentVerified)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
            return CommentViewHolder(v)
        }

        override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
            val c = comments[position]
            holder.tvAuthor.text  = c.authorName
            holder.tvText.text    = c.text
            holder.tvDate.text    = sdf.format(Date(c.createdAt))
            holder.tvVerified.visibility = if (c.isVerified) View.VISIBLE else View.GONE
        }

        override fun getItemCount() = comments.size
    }
}
