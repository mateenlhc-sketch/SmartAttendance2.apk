package com.example.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

val SCHOOL_CLASSES = listOf("Class 6", "Class 7", "Class 8", "Class 9")

@IgnoreExtraProperties
data class TeacherProfile(
    @DocumentId val id: String = "",
    val teacherId: String = "",
    val displayName: String = "",
    val email: String = "",
    val schoolName: String = "ABC High School",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolClassDoc(
    @DocumentId val id: String = "",
    val teacherId: String = "",
    val className: String = "",
    val schoolName: String = "ABC High School",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudentDoc(
    @DocumentId val id: String = "",
    val teacherId: String = "",
    val name: String = "",
    val rollNumber: String = "",
    val className: String = "",
    val photoBase64: String? = null,
    val faceEmbedding: List<Double> = emptyList(),
    val sampleCount: Long = 0L,
    val registrationDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toFloatEmbedding(): FloatArray = FloatArray(faceEmbedding.size) { idx ->
        faceEmbedding[idx].toFloat()
    }

    val hasValidBiometrics: Boolean
        get() = faceEmbedding.size >= 128 && sampleCount >= 1L
}

@IgnoreExtraProperties
data class AttendanceDoc(
    @DocumentId val id: String = "",
    val teacherId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val rollNumber: String = "",
    val className: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "Present",
    val verificationMethod: String = "AI Face Recognition",
    val confidenceScore: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
