package com.towmech.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp

@Composable
fun TowMechTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = {
            Text(
                text = label,
                fontSize = 16.sp,
                color = Color(0xFF0033A0)
            )
        },
        textStyle = TextStyle(
            fontSize = 18.sp,
            color = Color(0xFF0033A0)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFF0033A0),
            unfocusedTextColor = Color(0xFF0033A0),
            focusedBorderColor = Color(0xFF0033A0),
            unfocusedBorderColor = Color(0xFF0033A0),
            focusedLabelColor = Color(0xFF0033A0),
            unfocusedLabelColor = Color(0xFF0033A0),
            cursorColor = Color(0xFF0033A0)
        )
    )
}