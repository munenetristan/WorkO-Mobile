package com.towmech.app.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun CustomerProfileScreen(
    onLogout: () -> Unit,

    // ✅ Wire Support / Report Problem / Privacy(Delete) to Support screen
    // Example usage in navigation:
    // CustomerProfileScreen(onLogout = {...}, onOpenSupport = { navController.navigate("support") })
    onOpenSupport: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)

    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    // fetched (read-only)
    var fullName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }

    // registration details (read-only)
    var birthday by remember { mutableStateOf("") }
    var nationalityType by remember { mutableStateOf("") }
    var saIdNumber by remember { mutableStateOf("") }
    var passportNumber by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }

    // editable
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    // dialogs
    var showEditPhone by remember { mutableStateOf(false) }
    var showEditEmail by remember { mutableStateOf(false) }
    var showEditPassword by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun openExternal(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("Could not open link")
        }
    }

    fun loadProfile() {
        scope.launch {
            try {
                loading = true
                errorMessage = ""

                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = "Session expired. Please login again."
                    loading = false
                    return@launch
                }

                val res = ApiClient.apiService.getProfile("Bearer $token")
                val user = res.user

                fullName = user?.name ?: "Unknown"
                phone = user?.phone ?: ""
                email = user?.email ?: ""
                role = user?.role ?: "Customer"

                // these fields will appear after backend GET /api/auth/me select update
                birthday = tryReadString(user, "birthday") ?: ""
                nationalityType = tryReadString(user, "nationalityType") ?: ""
                saIdNumber = tryReadString(user, "saIdNumber") ?: ""
                passportNumber = tryReadString(user, "passportNumber") ?: ""
                country = tryReadString(user, "country") ?: ""

                loading = false
            } catch (e: HttpException) {
                loading = false
                val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                errorMessage =
                    "Failed to load profile. Please try again."
            } catch (e: Exception) {
                loading = false
                errorMessage = "Failed to load profile. Please try again."
            }
        }
    }

    fun saveEdits(newPhone: String? = null, newEmail: String? = null, newPassword: String? = null) {
        scope.launch {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Please login again.")
                    return@launch
                }

                val body = mutableMapOf<String, String>()
                if (!newPhone.isNullOrBlank()) body["phone"] = newPhone.trim()
                if (!newEmail.isNullOrBlank()) body["email"] = newEmail.trim()
                if (!newPassword.isNullOrBlank()) body["password"] = newPassword.trim()

                if (body.isEmpty()) return@launch

                ApiClient.apiService.updateMyProfile("Bearer $token", body)

                toast("Updated ✅")
                loadProfile()

            } catch (e: HttpException) {
                toast("Update failed. Please try again.")
            } catch (e: Exception) {
                toast("Update failed. Please try again.")
            }
        }
    }

    LaunchedEffect(Unit) { loadProfile() }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(25.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(100.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "My Profile",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (loading) {
                CircularProgressIndicator(color = darkBlue)
                Spacer(modifier = Modifier.height(15.dp))
                Text("Loading profile...", color = darkBlue, fontSize = 16.sp)
                return@Column
            }

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Color.Red)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { loadProfile() }) { Text("Retry") }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ✅ Registration details (read-only)
            ProfileCard(title = "User Details") {
                ProfileItem(Icons.Default.Person, "Full Name", fullName) {}
                ProfileItem(Icons.Default.Badge, "Role", role) {}

                if (birthday.isNotBlank()) {
                    ProfileItem(Icons.Default.Cake, "Birthday", birthday) {}
                }
                if (nationalityType.isNotBlank()) {
                    ProfileItem(Icons.Default.Flag, "Nationality Type", nationalityType) {}
                }
                if (saIdNumber.isNotBlank()) {
                    ProfileItem(Icons.Default.Badge, "SA ID Number", saIdNumber) {}
                }
                if (passportNumber.isNotBlank()) {
                    ProfileItem(Icons.Default.Badge, "Passport Number", passportNumber) {}
                }
                if (country.isNotBlank()) {
                    ProfileItem(Icons.Default.Public, "Country", country) {}
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            // ✅ ONLY editable: phone/email/password
            ProfileCard(title = "Account") {
                ProfileItem(Icons.Default.Call, "Phone Number", phone.ifBlank { "Not available" }) {
                    showEditPhone = true
                }
                ProfileItem(Icons.Default.Email, "Email Address", email.ifBlank { "Not available" }) {
                    showEditEmail = true
                }
                ProfileItem(Icons.Default.Lock, "Password", "Change password") {
                    showEditPassword = true
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            // ✅ Support + Legal
            ProfileCard(title = "Support") {

                // 2) Support opens support screen
                ProfileItem(Icons.Default.SupportAgent, "Support", "Get help") {
                    onOpenSupport()
                }

                // 3) Report Problem opens support screen
                ProfileItem(Icons.Default.Report, "Report Problem", "Tell us what happened") {
                    onOpenSupport()
                }

                // 4) Terms external
                ProfileItem(Icons.Default.Description, "Terms & Conditions", "Open in browser") {
                    openExternal("https://towmech.com/terms")
                }

                // 5) Privacy external
                ProfileItem(Icons.Default.PrivacyTip, "Privacy Policy", "Open in browser") {
                    openExternal("https://towmech.com/privacy")
                }

                // 6) Privacy -> support deletion request
                ProfileItem(Icons.Default.DeleteForever, "Privacy", "Request account deletion") {
                    onOpenSupport()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ✅ Logout unchanged
            Button(
                onClick = {
                    TokenManager.clearToken(context)
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(40.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(25.dp))
        }

        // ==========================
        // Edit dialogs
        // ==========================
        if (showEditPhone) {
            EditSingleFieldDialog(
                title = "Edit Phone Number",
                label = "Phone",
                keyboardType = KeyboardType.Phone,
                initialValue = phone,
                onDismiss = { showEditPhone = false },
                onSave = { newValue ->
                    showEditPhone = false
                    saveEdits(newPhone = newValue)
                }
            )
        }

        if (showEditEmail) {
            EditSingleFieldDialog(
                title = "Edit Email Address",
                label = "Email",
                keyboardType = KeyboardType.Email,
                initialValue = email,
                onDismiss = { showEditEmail = false },
                onSave = { newValue ->
                    showEditEmail = false
                    saveEdits(newEmail = newValue)
                }
            )
        }

        if (showEditPassword) {
            EditPasswordDialog(
                onDismiss = { showEditPassword = false },
                onSave = { newPass ->
                    showEditPassword = false
                    saveEdits(newPassword = newPass)
                }
            )
        }
    }
}

// =====================================================
// Helpers (same file to avoid unresolved refs)
// =====================================================

@Composable
fun ProfileCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val darkBlue = Color(0xFF0033A0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun ProfileItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    val darkBlue = Color(0xFF0033A0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = darkBlue)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = darkBlue)
            Text(value, fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun EditSingleFieldDialog(
    title: String,
    label: String,
    keyboardType: KeyboardType,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(value.trim()) }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditPasswordDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it; err = "" },
                    label = { Text("New Password") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; err = "" },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (err.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(err, color = Color.Red, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val p1 = pass.trim()
                val p2 = confirm.trim()

                if (p1.length < 6) {
                    err = "Password must be at least 6 characters"
                    return@Button
                }
                if (p1 != p2) {
                    err = "Passwords do not match"
                    return@Button
                }
                onSave(p1)
            }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun tryReadString(anyObj: Any?, fieldName: String): String? {
    if (anyObj == null) return null

    try {
        val getter = "get" + fieldName.replaceFirstChar { it.uppercase() }
        val m = anyObj.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
        val v = m?.invoke(anyObj) as? String
        if (!v.isNullOrBlank()) return v
    } catch (_: Exception) {}

    try {
        val f = anyObj.javaClass.declaredFields.firstOrNull { it.name == fieldName }
        if (f != null) {
            f.isAccessible = true
            val v = f.get(anyObj) as? String
            if (!v.isNullOrBlank()) return v
        }
    } catch (_: Exception) {}

    return null
}