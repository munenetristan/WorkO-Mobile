package com.towmech.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JobRequestPopupOverlay(
    title: String,
    pickup: String,
    dropoff: String,
    providerPayoutText: String,
    estimatedDistanceText: String? = null, // ✅ NEW
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)
    val red = Color(0xFFD32F2F)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkBlue
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = providerPayoutText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = green
                )

                // ✅ Estimated Distance (same style line, doesn't break UI)
                if (!estimatedDistanceText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = estimatedDistanceText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Pickup:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = darkBlue)
                Text(pickup, fontSize = 15.sp, color = darkBlue)

                Spacer(modifier = Modifier.height(10.dp))

                Text("Dropoff:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = darkBlue)
                Text(dropoff, fontSize = 15.sp, color = darkBlue)

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green)
                ) {
                    Text("Accept ✅", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onReject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = red)
                ) {
                    Text("Reject ❌", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}