package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderProminent
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun YouTubeGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "YouTube Live Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                GuideStep(
                    stepNumber = "1",
                    title = "Open YouTube Live Studio",
                    description = "Go to studio.youtube.com, click '+ CREATE' in the top right, then select 'Go Live'."
                )

                GuideStep(
                    stepNumber = "2",
                    title = "Copy your Stream Key",
                    description = "Under 'Stream settings', look for 'Stream key' (hidden behind dots). Click 'Copy'."
                )

                GuideStep(
                    stepNumber = "3",
                    title = "Choose Orientation",
                    description = "Select 'Horizontal 16:9' for widescreen games, or 'Vertical 9:16' for YouTube Shorts live."
                )

                GuideStep(
                    stepNumber = "4",
                    title = "Enable Face Cam (Optional)",
                    description = "Enable Face Cam to show your camera cleanly with zero marks. Touch the camera to adjust size & zoom (buttons auto-hide after 3s)."
                )

                GuideStep(
                    stepNumber = "5",
                    title = "Tap 'Start Stream'",
                    description = "Accept the Android screen capture prompt. Your live broadcast begins immediately on YouTube!"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://studio.youtube.com/channel/live/livestreaming")
                    )
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureWhite,
                    contentColor = PureBlack
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_guide_open_studio")
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open YouTube Studio", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderProminent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun GuideStep(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(PureWhite, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = PureBlack,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
