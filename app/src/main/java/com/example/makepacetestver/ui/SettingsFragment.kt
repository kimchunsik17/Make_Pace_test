package com.example.makepacetestver.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.makepacetestver.LoginActivity
import com.example.makepacetestver.R
import com.example.makepacetestver.data.UserPreferences
import com.example.makepacetestver.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var userPrefs: UserPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        userPrefs = UserPreferences(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserProfile()

        binding.btnSave.setOnClickListener {
            saveProfile()
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                val researchId = document.getString("researchId") ?: return@addOnSuccessListener
                db.collection("research_data").document(researchId).get()
                    .addOnSuccessListener { researchDoc ->
                        _binding?.let {
                            it.etAge.setText(researchDoc.getLong("age")?.toString() ?: "")
                            it.etHeight.setText(researchDoc.getDouble("height")?.toString() ?: "")
                            it.etWeight.setText(researchDoc.getDouble("weight")?.toString() ?: "")
                            
                            val gender = researchDoc.getString("gender") ?: ""
                            if (gender == "male") it.rbMale.isChecked = true
                            else if (gender == "female") it.rbFemale.isChecked = true
                        }
                    }
            }
    }

    private fun saveProfile() {
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
            Toast.makeText(requireContext(), "모든 정보를 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                val researchId = document.getString("researchId") ?: return@addOnSuccessListener
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
                        if (isAdded) Toast.makeText(requireContext(), "정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun logout() {
        auth.signOut()
        userPrefs.clear()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
