// CreateTowTruckJobScreen.kt
package com.towmech.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateTowTruckJobScreen(
    onBack: () -> Unit,
    onJobCreated: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var pickupAddress by remember { mutableStateOf("") }
    var dropoffAddress by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Tow Truck Request", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Job Title") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = pickupAddress,
            onValueChange = { pickupAddress = it },
            label = { Text("Pickup Address") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = dropoffAddress,
            onValueChange = { dropoffAddress = it },
            label = { Text("Dropoff Address") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                // ✅ You can later connect API call here
                if (title.isNotEmpty() && pickupAddress.isNotEmpty() && dropoffAddress.isNotEmpty()) {
                    onJobCreated()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Tow Request")
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
            text = "Killian Digital Solutions © 2026",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}