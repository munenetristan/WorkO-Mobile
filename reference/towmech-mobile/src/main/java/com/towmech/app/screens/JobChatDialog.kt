package com.towmech.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.towmech.app.data.ChatMessageDto
import kotlinx.coroutines.launch

@Composable
fun JobChatDialog(
    show: Boolean,
    title: String = "Chat",
    myUserId: String?,
    messages: List<ChatMessageDto>,
    canSend: Boolean,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    if (!show) return

    var input by remember { mutableStateOf("") }
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ✅ auto scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 520.dp),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ✅ Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider()

                // ✅ Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    reverseLayout = false
                ) {
                    items(messages) { msg ->
                        val isMine = (myUserId != null && msg.senderId == myUserId)

                        val bubbleColor =
                            if (isMine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer

                        val textColor =
                            if (isMine) Color.White
                            else MaterialTheme.colorScheme.onSecondaryContainer

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(vertical = 4.dp)
                                    .background(bubbleColor, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = msg.text ?: "",
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                Divider()

                // ✅ Input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type message...") },
                        enabled = canSend && !sending,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                onSend(text)
                                input = ""
                            }
                        },
                        enabled = canSend && !sending && input.trim().isNotEmpty()
                    ) {
                        Text(if (sending) "..." else "Send")
                    }
                }

                // ✅ Footer note when blocked
                if (!canSend) {
                    Text(
                        text = "Chat is locked / unavailable for this job.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 10.dp),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}