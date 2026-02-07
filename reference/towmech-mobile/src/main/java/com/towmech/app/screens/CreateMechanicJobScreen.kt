// CreateMechanicJobScreen.kt
package com.towmech.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * NOTE:
 * This screen is still a simple placeholder in your project (no API here yet).
 * We ONLY added:
 * - mechanic category selection (UI)
 * - disclaimer text (UI)
 * and kept the existing "onJobCreated()" trigger unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMechanicJobScreen(
    onBack: () -> Unit,
    onJobCreated: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    // ✅ Mechanic category (UI only here)
    val mechanicCategories = listOf(
        "General Mechanic",
        "Engine Mechanic",
        "Gearbox Mechanic",
        "Suspension & Alignment",
        "Tyre and rims",
        "Car wiring and Diagnosis"
    )
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }

    val disclaimerText =
        "Final fee is NOT predetermined. The mechanic will diagnose and agree the final price after pairing."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Mechanic Request", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Job Title") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ✅ Mechanic category selection (tick-box flow is implemented elsewhere in provider onboarding;
        // this is customer-side category selection)
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded }
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Mechanic Category") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                mechanicCategories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = {
                            selectedCategory = cat
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Describe the problem") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Your Location / Address") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ✅ Disclaimer (required by your spec)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Important", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Text(disclaimerText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                // ✅ Later connect API call here
                if (title.isNotEmpty() && description.isNotEmpty() && address.isNotEmpty()) {
                    onJobCreated()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Mechanic Request")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Killian Digital Solutions © 2025",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}