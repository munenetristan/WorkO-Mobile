package com.towmech.app.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.towmech.app.api.ApiClient
import com.towmech.app.api.CreateSupportTicketRequest
import com.towmech.app.api.SupportTicket
import com.towmech.app.data.TokenManager
import com.towmech.app.navigation.Routes
import kotlinx.coroutines.launch
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSupportScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var tickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }

    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    data class CategoryOption(val label: String, val backendType: String)

    val categories = remember {
        listOf(
            CategoryOption("Job did not happen", "JOB"),
            CategoryOption("Recovering lost item", "LOST_ITEM"),
            CategoryOption("Payment charged twice on card", "PAYMENT"),
            CategoryOption("Provider was rude", "SAFETY"),
            CategoryOption("Provider (TowTruck) charged higher", "PAYMENT"),
            CategoryOption("Wrong car / number plate", "JOB"),
            CategoryOption("Refund", "PAYMENT"),
            CategoryOption("Provider arrived late", "JOB"),
            CategoryOption("OTHERS", "OTHER")
        )
    }

    val priorities = remember {
        listOf(
            "Low" to "LOW",
            "Medium/normal" to "MEDIUM",
            "Urgent/high" to "HIGH"
        )
    }

    var categoryExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf<CategoryOption?>(null) }
    var selectedPriority by remember { mutableStateOf(priorities.first()) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun loadTickets() {
        scope.launch {
            try {
                loading = true
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Please login again.")
                    return@launch
                }
                val res = ApiClient.apiService.getSupportTickets("Bearer $token")
                tickets = res.tickets ?: emptyList()
            } catch (e: Exception) {
                toast("Failed to load tickets: ${e.message}")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadTickets() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Support", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Subject") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // TYPE (Category)
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedCategory?.label ?: "Choose Category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Category dropdown")
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.label) },
                            onClick = {
                                selectedCategory = opt
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            // PRIORITY
            ExposedDropdownMenuBox(
                expanded = priorityExpanded,
                onExpandedChange = { priorityExpanded = !priorityExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedPriority.first,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Priority") },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Priority dropdown")
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { priorityExpanded = false }
                ) {
                    priorities.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.first) },
                            onClick = {
                                selectedPriority = opt
                                priorityExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (subject.isBlank()) return@Button toast("Subject is required.")
                if (message.isBlank()) return@Button toast("Message is required.")
                if (selectedCategory == null) return@Button toast("Please choose a category.")

                scope.launch {
                    try {
                        loading = true
                        val token = TokenManager.getToken(context)
                        if (token.isNullOrBlank()) {
                            toast("Session expired. Please login again.")
                            return@launch
                        }

                        ApiClient.apiService.createSupportTicket(
                            token = "Bearer $token",
                            request = CreateSupportTicketRequest(
                                subject = subject.trim(),
                                message = message.trim(),
                                type = selectedCategory!!.backendType,
                                priority = selectedPriority.second
                            )
                        )

                        subject = ""
                        message = ""
                        selectedCategory = null
                        selectedPriority = priorities.first()

                        toast("Ticket sent ✅")
                        loadTickets()

                    } catch (e: Exception) {
                        if (e is HttpException) toast("Failed: HTTP ${e.code()}")
                        else toast("Failed to create ticket: ${e.message}")
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text(if (loading) "Please wait..." else "Send Ticket")
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("My Tickets", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { loadTickets() }, enabled = !loading) { Text("Refresh") }
        }

        Spacer(Modifier.height(8.dp))

        if (loading && tickets.isEmpty()) {
            CircularProgressIndicator()
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(tickets) { t ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable {
                                val id = t._id?.trim().orEmpty()
                                if (id.isNotBlank()) {
                                    // ✅ matches Routes.CUSTOMER_SUPPORT_TICKET_ROUTE = "customer_support_ticket/{ticketId}"
                                    navController.navigate(Routes.CUSTOMER_SUPPORT_TICKET + "/$id")
                                }
                            }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.subject ?: "No subject", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Type: ${t.type ?: "UNKNOWN"}   |   Priority: ${t.priority ?: "UNKNOWN"}")
                            Spacer(Modifier.height(4.dp))
                            Text("Status: ${t.status ?: "UNKNOWN"}")
                            Spacer(Modifier.height(6.dp))
                            Text(t.message ?: "")
                        }
                    }
                }
            }
        }
    }
}