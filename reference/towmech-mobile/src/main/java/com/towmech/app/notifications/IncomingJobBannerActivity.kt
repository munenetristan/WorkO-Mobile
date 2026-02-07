package com.towmech.app.notifications

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towmech.app.MainActivity

class IncomingJobBannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Make it a top banner (1/4 screen height)
        makeTopQuarterBanner()

        val title = intent.getStringExtra("title") ?: "TowMech"
        val body = intent.getStringExtra("body") ?: "You have a new job request"
        val open = intent.getStringExtra("open") ?: "job_requests"
        val jobId = intent.getStringExtra("jobId")

        setContent {
            val green = remember { Color(0xFF007A3D) }
            val dark = remember { Color(0xFF111111) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(12.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = dark
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = body,
                        fontSize = 15.sp,
                        color = dark
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                // ✅ Open the app exactly like notification tap
                                val i = Intent(this@IncomingJobBannerActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    putExtra("open", open)
                                    if (!jobId.isNullOrBlank()) putExtra("jobId", jobId)
                                }
                                startActivity(i)
                                finish()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = green)
                        ) {
                            Text("Open", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { finish() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDDDDDD))
                        ) {
                            Text("Dismiss", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    private fun makeTopQuarterBanner() {
        // Ensure it shows over lockscreen too (manifest already has showWhenLocked/turnScreenOn)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        val quarterHeight = (metrics.heightPixels * 0.25f).toInt()

        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, quarterHeight)

        val params = window.attributes
        params.gravity = Gravity.TOP
        params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        window.attributes = params
    }
}