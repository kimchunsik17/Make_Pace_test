package com.example.makepacetestver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.makepacetestver.data.UserPreferences
import com.example.makepacetestver.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var userPrefs: UserPreferences

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "구글 로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        userPrefs = UserPreferences(this)

        // 이미 로그인되어 있고 로컬에 프로필이 있다면 바로 시작 (오프라인 지원)
        if (auth.currentUser != null && userPrefs.getResearchId() != null) {
            startMainActivity()
        }

        binding.btnGoogleLogin.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Firebase 연동 시 자동 생성됨
            .requestEmail()
            .build()

        val signInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(signInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    checkAndSetupProfile()
                } else {
                    Toast.makeText(this, "인증 실패", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkAndSetupProfile() {
        val user = auth.currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    val researchId = UUID.randomUUID().toString()
                    val userProfile = hashMapOf(
                        "uid" to user.uid,
                        "email" to user.email,
                        "researchId" to researchId
                    )
                    db.collection("users").document(user.uid).set(userProfile)
                        .addOnSuccessListener {
                            userPrefs.saveProfile(researchId, 0, 0f, 0f, "unspecified")
                            startSetupActivity()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "프로필 생성 실패: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    val researchId = document.getString("researchId")
                    if (researchId == null) {
                        // researchId 없는 비정상 계정 → 재설정
                        userPrefs.clear()
                        startSetupActivity()
                        return@addOnSuccessListener
                    }
                    db.collection("research_data").document(researchId).get()
                        .addOnSuccessListener { researchDoc ->
                            val age = researchDoc.getLong("age")?.toInt() ?: 0
                            val height = researchDoc.getDouble("height")?.toFloat() ?: 0f
                            val weight = researchDoc.getDouble("weight")?.toFloat() ?: 0f
                            val gender = researchDoc.getString("gender") ?: "unspecified"
                            userPrefs.saveProfile(researchId, age, height, weight, gender)
                            if (age == 0) startSetupActivity() else startMainActivity()
                        }
                        .addOnFailureListener { e ->
                            // 네트워크 실패 — 로컬 캐시 있으면 메인으로, 없으면 에러 표시
                            if (userPrefs.getResearchId() != null) {
                                startMainActivity()
                            } else {
                                Toast.makeText(this, "네트워크 오류: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                if (userPrefs.getResearchId() != null) {
                    startMainActivity()
                } else {
                    Toast.makeText(this, "서버 연결 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun startSetupActivity() {
        startActivity(Intent(this, SetupActivity::class.java))
        finish()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
