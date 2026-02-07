package com.towmech.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.towmech.app.R

@Composable
fun RoleSelectScreen(
    onBack: () -> Unit,
    onTowTruck: () -> Unit,
    onMechanic: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // ✅ Background Map Image
        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = "Background Map",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(35.dp))

            // ✅ TowMech Logo
            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "TowMech Logo",
                modifier = Modifier
                    .height(95.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Sign Up As",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(35.dp))

            val buttonModifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(20.dp))

            val yellow = Color(0xFFFFB81C)
            val red = Color(0xFFDE3831)
            val blue = Color(0xFF003DA5)

            // ✅ TOW TRUCK BUTTON
            Button(
                onClick = onTowTruck,
                modifier = buttonModifier,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = yellow)
            ) {
                Text(
                    "TowTruck",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ✅ MECHANIC BUTTON
            Button(
                onClick = onMechanic,
                modifier = buttonModifier,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = red)
            ) {
                Text(
                    "Mechanic",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ✅ BACK BUTTON
            Button(
                onClick = onBack,
                modifier = buttonModifier,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = blue)
            ) {
                Text(
                    "Back",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = R.drawable.towmech_hero),
                contentDescription = "Footer Hero Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}