package com.example.makepacetestver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

        // 이미 로그인되어 있는지 확인
        if (auth.currentUser != null) {
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
                    
                    // 1. 식별 정보 저장
                    db.collection("users").document(user.uid).set(userProfile)
                        .addOnSuccessListener {
                            // 2. 신규 유저는 무조건 설정 화면으로 이동
                            startSetupActivity()
                        }
                } else {
                    // 기존 유저라면 설정 데이터가 있는지 확인
                    val researchId = document.getString("researchId") ?: return@addOnSuccessListener
                    db.collection("research_data").document(researchId).get()
                        .addOnSuccessListener { researchDoc ->
                            val age = researchDoc.getLong("age") ?: 0
                            if (age == 0L) {
                                startSetupActivity()
                            } else {
                                startMainActivity()
                            }
                        }
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
