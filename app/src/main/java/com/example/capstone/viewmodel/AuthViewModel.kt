package com.example.capstone.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.capstone.models.Batch
import com.example.capstone.models.School
import com.example.capstone.repository.AuthRepository

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _registerState = MutableLiveData<Pair<Boolean, String?>>()
    val registerState: LiveData<Pair<Boolean, String?>> = _registerState

    fun register(
        name: String,
        email: String,
        password: String,
        role: String,
        phone: String,
        dob: String,
        gender: String,
        profileImageUri: Uri?
    ) {
        repository.registerUser(name, email, password, role, phone, dob, gender, profileImageUri) { success, message ->
            _registerState.value = Pair(success, message)
        }
    }
    
    /**
     * NEW: Register user with school and batch information (multi-tenancy)
     */
    fun registerWithSchool(
        name: String,
        email: String,
        password: String,
        role: String,
        phone: String,
        dob: String,
        gender: String,
        profileImageUri: Uri?,
        school: School,
        batch: Batch?,
        isFirstTeacher: Boolean
    ) {
        repository.registerUserWithSchool(
            name, email, password, role, phone, dob, gender,
            profileImageUri, school, batch, isFirstTeacher
        ) { success, message ->
            _registerState.value = Pair(success, message)
        }
    }
}
