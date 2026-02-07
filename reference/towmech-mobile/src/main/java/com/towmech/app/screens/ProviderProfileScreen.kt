package com.towmech.app.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.UpdateProviderMeRequest
import com.towmech.app.data.TokenManager
import com.towmech.app.notifications.ProviderForegroundService
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream

// =====================================================
// ✅ Local helpers (NO name collision with Customer screen)
// =====================================================
private fun setProviderOnlinePref(context: Context, value: Boolean) {
    val sp = context.getSharedPreferences("provider_status_pref", Context.MODE_PRIVATE)
    sp.edit().putBoolean("is_online", value).apply()
}

private fun getFileName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        it.moveToFirst()
        if (nameIndex >= 0) it.getString(nameIndex) else "selected_file"
    } ?: "selected_file"
}

private fun persistReadPermissionIfPossible(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: Exception) {
        // some providers don't allow persistable permission
    }
}

private fun sanitizeFileName(original: String): String {
    val cleaned = original.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return cleaned.ifBlank { "upload_file" }
}

private fun getFileExtension(name: String): String {
    val idx = name.lastIndexOf('.')
    return if (idx != -1 && idx < name.length - 1) name.substring(idx) else ""
}

/**
 * ✅ IMPORTANT:
 * The `partName` must match backend multer field names:
 * idDocument, workshopProof, license, vehicleProof
 */
private fun uriToPart(ctx: Context, uri: Uri, partName: String): MultipartBody.Part {
    val originalName = getFileName(ctx, uri)
    val safeName = sanitizeFileName(originalName)
    val ext = getFileExtension(safeName)

    val finalName = if (ext.isNotBlank()) {
        "${partName}_${System.currentTimeMillis()}$ext"
    } else {
        "${partName}_${System.currentTimeMillis()}"
    }

    val file = File(ctx.cacheDir, finalName)

    ctx.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    }

    val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
    val body = file.asRequestBody(mime.toMediaTypeOrNull())

    Log.d("UPLOAD_DEBUG", "Part=$partName file=${file.name} mime=$mime size=${file.length()}")

    return MultipartBody.Part.createFormData(partName, file.name, body)
}

// =====================================================
// ✅ Document row
// =====================================================
@Composable
private fun ProviderDocumentRow(
    label: String,
    uploadedUrl: String?,
    pickedUri: Uri?,
    onPick: () -> Unit
) {
    val context = LocalContext.current
    val pickedName = remember(pickedUri) { pickedUri?.let { getFileName(context, it) } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)

                Text(
                    text = when {
                        !uploadedUrl.isNullOrBlank() -> "✅ Uploaded"
                        pickedUri != null -> "🟡 Selected (not uploaded yet)"
                        else -> "❌ Not uploaded"
                    },
                    fontSize = 13.sp,
                    color = Color.Black
                )

                if (!pickedName.isNullOrBlank()) {
                    Text("File: $pickedName", fontSize = 12.sp, color = Color.Black)
                }
            }

            OutlinedButton(onClick = onPick, shape = RoundedCornerShape(12.dp)) {
                Text("Choose", color = Color.Black)
            }
        }

        Divider(modifier = Modifier.padding(vertical = 10.dp))
    }
}

// =====================================================
// ✅ Dialog helpers (Customer-style edit)
// =====================================================
@Composable
private fun EditSingleFieldDialog(
    title: String,
    label: String,
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = pass1,
                    onValueChange = { pass1 = it },
                    label = { Text("New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pass2,
                    onValueChange = { pass2 = it },
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = Color.Red, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val a = pass1.trim()
                val b = pass2.trim()
                if (a.length < 6) {
                    error = "Password must be at least 6 characters."
                    return@Button
                }
                if (a != b) {
                    error = "Passwords do not match."
                    return@Button
                }
                onSave(a)
            }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selected: String?,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPicked(opt) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (opt == selected),
                            onClick = { onPicked(opt) }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(opt)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// =====================================================
// ✅ Card + item UI (provider-only names to avoid collisions)
// =====================================================
@Composable
private fun ProviderSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val darkBlue = Color(0xFF0033A0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = darkBlue)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ProviderRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val darkBlue = Color(0xFF0033A0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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

// =====================================================
// ✅ MAIN SCREEN
// =====================================================
@Composable
fun ProviderProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenSupport: (() -> Unit)? = null,   // ✅ wire this to your Support screen route
    termsUrl: String = "https://towmech.com/terms",
    privacyUrl: String = "https://towmech.com/privacy"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)
    val red = Color(0xFFB00020)

    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    // ✅ User fields
    var fullName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }

    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    var birthday by remember { mutableStateOf("") }
    var nationalityType by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var saIdNumber by remember { mutableStateOf("") }
    var passportNumber by remember { mutableStateOf("") }

    // ✅ Provider fields
    var verificationStatus by remember { mutableStateOf("PENDING") }
    var selectedTowTruckType by remember { mutableStateOf<String?>(null) }
    var selectedMechanicCategory by remember { mutableStateOf<String?>(null) }

    // ✅ Docs from backend
    var idDocUrl by remember { mutableStateOf<String?>(null) }
    var licenseUrl by remember { mutableStateOf<String?>(null) }
    var vehicleProofUrl by remember { mutableStateOf<String?>(null) }
    var workshopProofUrl by remember { mutableStateOf<String?>(null) }

    // ✅ Picked URIs
    var idDocUri by remember { mutableStateOf<Uri?>(null) }
    var workshopUri by remember { mutableStateOf<Uri?>(null) }
    var licenseUri by remember { mutableStateOf<Uri?>(null) }
    var vehicleUri by remember { mutableStateOf<Uri?>(null) }

    // ✅ Dialog toggles
    var showEditPhone by remember { mutableStateOf(false) }
    var showEditEmail by remember { mutableStateOf(false) }
    var showEditPassword by remember { mutableStateOf(false) }

    var showTowTypePicker by remember { mutableStateOf(false) }
    var showMechCatPicker by remember { mutableStateOf(false) }

    var saving by remember { mutableStateOf(false) }

    val isApproved = verificationStatus.equals("APPROVED", ignoreCase = true)

    // ✅ Your exact TowTruck types (the 6 you showed)
    val towTruckTypes = remember {
        listOf(
            "Hook & Chain",
            "Wheel-Lift",
            "Flatbed/Roll Back",
            "Boom Trucks(With Crane)",
            "Integrated / Wrecker",
            "Heavy-Duty Rotator(Recovery)"
        )
    }

    val mechanicCategories = remember {
        listOf(
            "General Mechanic",
            "Engine Mechanic",
            "Gearbox Mechanic",
            "Suspension & Alignment",
            "Tyre and rims",
            "Car wiring and Diagnosis"
        )
    }

    fun openExternal(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open link.", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadProfile() {
        scope.launch {
            try {
                loading = true
                errorMessage = ""

                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = "Session expired. Login again."
                    loading = false
                    return@launch
                }

                val res = ApiClient.apiService.getProviderMe("Bearer $token")
                val user = res.user

                if (user == null) {
                    errorMessage = "Profile not found."
                    loading = false
                    return@launch
                }

                fullName = user.name ?: listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { "Provider" }
                role = user.role ?: ""

                email = user.email ?: ""
                phone = user.phone ?: ""

                birthday = user.birthday ?: ""
                nationalityType = user.nationalityType ?: ""
                country = user.country ?: ""
                saIdNumber = user.saIdNumber ?: ""
                passportNumber = user.passportNumber ?: ""

                verificationStatus = user.providerProfile?.verificationStatus ?: "PENDING"

                // ✅ TowTruck / Mechanic current selections
                selectedTowTruckType = user.providerProfile?.towTruckTypes?.firstOrNull()
                selectedMechanicCategory = user.providerProfile?.mechanicCategories?.firstOrNull()

                val docs = user.providerProfile?.verificationDocs
                idDocUrl = docs?.idDocumentUrl
                licenseUrl = docs?.licenseUrl
                vehicleProofUrl = docs?.vehicleProofUrl
                workshopProofUrl = docs?.workshopProofUrl

                loading = false
            } catch (e: HttpException) {
                loading = false
                val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                errorMessage = "Failed to load profile: HTTP ${e.code()}${if (!raw.isNullOrBlank()) "\n$raw" else ""}"
            } catch (e: Exception) {
                loading = false
                errorMessage = "Failed to load profile: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) { loadProfile() }

    // =====================================================
    // FILE PICKERS
    // =====================================================
    val pickId = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermissionIfPossible(context, it)
            idDocUri = it
        }
    }

    val pickSelfie = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermissionIfPossible(context, it)
            workshopUri = it
        }
    }

    val pickHuru = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermissionIfPossible(context, it)
            licenseUri = it
        }
    }

    val pickVehicle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermissionIfPossible(context, it)
            vehicleUri = it
        }
    }

    // =====================================================
    // ✅ Save phone/email/password using PATCH /api/auth/me (you already have ApiService.updateMyProfile)
    // =====================================================
    fun saveAccountEdits(payload: Map<String, String>, successToast: String) {
        scope.launch {
            try {
                saving = true
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    Toast.makeText(context, "Session expired. Login again.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                ApiClient.apiService.updateMyProfile("Bearer $token", payload)
                Toast.makeText(context, successToast, Toast.LENGTH_SHORT).show()

                loadProfile()
            } catch (e: HttpException) {
                val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                Toast.makeText(
                    context,
                    "Update failed: HTTP ${e.code()}${if (!raw.isNullOrBlank()) "\n$raw" else ""}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                saving = false
            }
        }
    }

    // =====================================================
    // ✅ Save provider type/category using PATCH /api/providers/me (route exists ✅)
    // =====================================================
    fun saveProviderSkillUpdate() {
        scope.launch {
            try {
                saving = true
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    Toast.makeText(context, "Session expired. Login again.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val isTowTruck = role.equals("TowTruck", ignoreCase = true)
                val isMechanic = role.equals("Mechanic", ignoreCase = true)

                val req = when {
                    isTowTruck && !selectedTowTruckType.isNullOrBlank() -> {
                        UpdateProviderMeRequest(towTruckTypes = listOf(selectedTowTruckType!!))
                    }
                    isMechanic && !selectedMechanicCategory.isNullOrBlank() -> {
                        UpdateProviderMeRequest(mechanicCategories = listOf(selectedMechanicCategory!!))
                    }
                    else -> null
                }

                if (req == null) {
                    Toast.makeText(context, "Please select an option.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                ApiClient.apiService.updateProviderMe("Bearer $token", req)
                Toast.makeText(context, "Provider profile updated ✅", Toast.LENGTH_SHORT).show()
                loadProfile()
            } catch (e: HttpException) {
                val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                Toast.makeText(
                    context,
                    "Update failed: HTTP ${e.code()}${if (!raw.isNullOrBlank()) "\n$raw" else ""}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                saving = false
            }
        }
    }

    // =====================================================
    // UPLOAD DOCS
    // =====================================================
    fun uploadDocs() {
        scope.launch {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    Toast.makeText(context, "Session expired. Login again.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                if (idDocUri == null && workshopUri == null && licenseUri == null && vehicleUri == null) {
                    Toast.makeText(context, "Please choose at least one document to upload.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                ApiClient.apiService.uploadProviderDocuments(
                    token = "Bearer $token",
                    idDocumentUrl = idDocUri?.let { uriToPart(context, it, "idDocument") },
                    licenseUrl = licenseUri?.let { uriToPart(context, it, "license") },
                    vehicleProofUrl = vehicleUri?.let { uriToPart(context, it, "vehicleProof") },
                    workshopProofUrl = workshopUri?.let { uriToPart(context, it, "workshopProof") }
                )

                Toast.makeText(context, "Documents submitted ✅", Toast.LENGTH_SHORT).show()

                idDocUri = null
                workshopUri = null
                licenseUri = null
                vehicleUri = null

                loadProfile()

            } catch (e: HttpException) {
                val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                Toast.makeText(
                    context,
                    "Upload failed: HTTP ${e.code()}${if (!raw.isNullOrBlank()) "\n$raw" else ""}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // =====================================================
    // UI
    // =====================================================
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
                Text(errorMessage, color = Color.Red, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { loadProfile() }) { Text("Retry") }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ✅ User Details (FULL)
            ProviderSectionCard(title = "User Details") {
                ProviderRowItem(Icons.Default.Person, "Full Name", fullName)
                ProviderRowItem(Icons.Default.Badge, "Role", role.ifBlank { "Provider" })

                ProviderRowItem(
                    Icons.Default.Verified,
                    "Verification",
                    verificationStatus.ifBlank { "PENDING" }
                )

                ProviderRowItem(Icons.Default.DateRange, "Date of Birth", birthday.ifBlank { "Not available" })
                ProviderRowItem(Icons.Default.Public, "Nationality Type", nationalityType.ifBlank { "Not available" })

                if (nationalityType.equals("ForeignNational", ignoreCase = true)) {
                    ProviderRowItem(Icons.Default.Flag, "Country", country.ifBlank { "Not available" })
                    ProviderRowItem(Icons.Default.Badge, "Passport Number", passportNumber.ifBlank { "Not available" })
                } else {
                    ProviderRowItem(Icons.Default.Badge, "SA ID Number", saIdNumber.ifBlank { "Not available" })
                }

                // ✅ Provider fields only after approval
                if (isApproved) {
                    if (role.equals("TowTruck", ignoreCase = true)) {
                        ProviderRowItem(
                            Icons.Default.Build,
                            "Provider Type",
                            selectedTowTruckType ?: "Select",
                            onClick = { showTowTypePicker = true }
                        )
                    }
                    if (role.equals("Mechanic", ignoreCase = true)) {
                        ProviderRowItem(
                            Icons.Default.Build,
                            "Mechanic Category",
                            selectedMechanicCategory ?: "Select",
                            onClick = { showMechCatPicker = true }
                        )
                    }

                    if ((role.equals("TowTruck", ignoreCase = true) && selectedTowTruckType != null) ||
                        (role.equals("Mechanic", ignoreCase = true) && selectedMechanicCategory != null)
                    ) {
                        Button(
                            onClick = { saveProviderSkillUpdate() },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
                        ) {
                            Text(if (saving) "Saving..." else "Save Provider Changes", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            // ✅ Account (editable only: phone/email/password)
            ProviderSectionCard(title = "Account") {
                ProviderRowItem(Icons.Default.Call, "Phone Number", phone.ifBlank { "Not available" }) { showEditPhone = true }
                ProviderRowItem(Icons.Default.Email, "Email Address", email.ifBlank { "Not available" }) { showEditEmail = true }
                ProviderRowItem(Icons.Default.Lock, "Password", "Change password") { showEditPassword = true }
            }

            Spacer(modifier = Modifier.height(15.dp))

            // ✅ Settings
            ProviderSectionCard(title = "Settings") {
                ProviderRowItem(
                    Icons.Default.SupportAgent,
                    "Support",
                    "Get help"
                ) {
                    onOpenSupport?.invoke()
                        ?: Toast.makeText(context, "Wire onOpenSupport to navigate to Support screen.", Toast.LENGTH_SHORT).show()
                }

                ProviderRowItem(
                    Icons.Default.Report,
                    "Report Problem",
                    "Tell us what happened"
                ) {
                    onOpenSupport?.invoke()
                        ?: Toast.makeText(context, "Wire onOpenSupport to navigate to Support screen.", Toast.LENGTH_SHORT).show()
                }

                ProviderRowItem(Icons.Default.Description, "Terms & Conditions", "Open in browser") {
                    openExternal(termsUrl)
                }

                ProviderRowItem(Icons.Default.PrivacyTip, "Privacy Policy", "Open in browser") {
                    openExternal(privacyUrl)
                }

                ProviderRowItem(Icons.Default.Delete, "Privacy", "Request account deletion") {
                    onOpenSupport?.invoke()
                        ?: Toast.makeText(context, "Wire onOpenSupport to Support (account deletion request).", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            // ✅ Docs card only when NOT approved (as you had)
            if (!isApproved) {
                ProviderSectionCard(title = "Verification Documents") {
                    ProviderDocumentRow("ID Document", idDocUrl, idDocUri) {
                        pickId.launch(arrayOf("image/*", "application/pdf", "*/*"))
                    }
                    ProviderDocumentRow("Selfie", workshopProofUrl, workshopUri) {
                        pickSelfie.launch(arrayOf("image/*", "*/*"))
                    }
                    ProviderDocumentRow("Huru Criminal Check", licenseUrl, licenseUri) {
                        pickHuru.launch(arrayOf("application/pdf", "*/*"))
                    }
                    ProviderDocumentRow("Proof of Vehicle", vehicleProofUrl, vehicleUri) {
                        pickVehicle.launch(arrayOf("image/*", "application/pdf", "*/*"))
                    }

                    Button(
                        onClick = { uploadDocs() },
                        modifier = Modifier.fillMaxWidth().height(55.dp),
                        shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = green)
                    ) {
                        Text("Submit Documents", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tip: After choosing files, you should still see “Selected”.",
                        fontSize = 12.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))
            }

            // ✅ Logout
            Button(
                onClick = {
                    ProviderForegroundService.stop(context)
                    setProviderOnlinePref(context, false)
                    TokenManager.clearToken(context)
                    Toast.makeText(context, "Logged out ✅", Toast.LENGTH_SHORT).show()
                    onLoggedOut()
                },
                colors = ButtonDefaults.buttonColors(containerColor = red),
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(40.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text("Back", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // ==========================
    // ✅ dialogs
    // ==========================
    if (showEditPhone) {
        EditSingleFieldDialog(
            title = "Edit Phone Number",
            label = "Phone Number",
            initialValue = phone,
            onDismiss = { showEditPhone = false },
            onSave = { newPhone ->
                showEditPhone = false
                saveAccountEdits(mapOf("phone" to newPhone), "Phone updated ✅")
            }
        )
    }

    if (showEditEmail) {
        EditSingleFieldDialog(
            title = "Edit Email Address",
            label = "Email",
            initialValue = email,
            onDismiss = { showEditEmail = false },
            onSave = { newEmail ->
                showEditEmail = false
                saveAccountEdits(mapOf("email" to newEmail), "Email updated ✅")
            }
        )
    }

    if (showEditPassword) {
        ChangePasswordDialog(
            onDismiss = { showEditPassword = false },
            onSave = { newPass ->
                showEditPassword = false
                saveAccountEdits(mapOf("password" to newPass), "Password updated ✅")
            }
        )
    }

    if (showTowTypePicker) {
        SingleChoiceDialog(
            title = "Select TowTruck Type",
            options = towTruckTypes,
            selected = selectedTowTruckType,
            onDismiss = { showTowTypePicker = false },
            onPicked = {
                selectedTowTruckType = it
                showTowTypePicker = false
            }
        )
    }

    if (showMechCatPicker) {
        SingleChoiceDialog(
            title = "Select Mechanic Category",
            options = mechanicCategories,
            selected = selectedMechanicCategory,
            onDismiss = { showMechCatPicker = false },
            onPicked = {
                selectedMechanicCategory = it
                showMechCatPicker = false
            }
        )
    }
}