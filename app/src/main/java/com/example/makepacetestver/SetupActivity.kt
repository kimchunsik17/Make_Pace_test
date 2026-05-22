package com.example.makepacetestver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.makepacetestver.data.UserPreferences
import com.example.makepacetestver.databinding.ActivitySetupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var userPrefs: UserPreferences
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userPrefs = UserPreferences(this)

        binding.btnComplete.setOnClickListener {
            saveProfileAndStart()
        }
    }

    private fun saveProfileAndStart() {
        val user = auth.currentUser ?: return
        val age = binding.etAge.text.toString().toIntOrNull() ?: 0
        val height = binding.etHeight.text.toString().toFloatOrNull() ?: 0f
        val weight = binding.etWeight.text.toString().toFloatOrNull() ?: 0f
        val gender = when (binding.rgGender.checkedRadioButtonId) {
            R.id.rbMale -> "male"
            R.id.rbFemale -> "female"
            else -> ""
        }

        if (age == 0 || height == 0f || weight == 0f || gender.isEmpty()) {
            Toast.makeText(this, "모든 정보를 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 먼저 사용자의 researchId를 가져옵니다.
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val researchId = document.getString("researchId")
                if (researchId != null) {
                    // 2. 익명 연구 데이터 업데이트
                    val researchData = hashMapOf(
                        "researchId" to researchId,
                        "age" to age,
                        "height" to height,
                        "weight" to weight,
                        "gender" to gender
                    )
                    
                    db.collection("research_data").document(researchId).set(researchData)
                        .addOnSuccessListener {
                            userPrefs.saveProfile(researchId, age, height, weight, gender)
                            startMainActivity()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "데이터 저장 실패", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
