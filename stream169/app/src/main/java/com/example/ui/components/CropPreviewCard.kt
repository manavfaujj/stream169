package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stream.model.StreamOrientation
import com.example.stream.model.StreamResolution
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PureGray
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.White

@Composable
fun CropPreviewCard(
    orientation: StreamOrientation,
    resolution: StreamResolution,
    onOrientationChanged: (StreamOrientation) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Stream Aspect Ratio",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "Choose output format (does not force mobile device screen rotation)",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 16:9 Landscape Option
                val is169 = orientation == StreamOrientation.HORIZONTAL_16_9
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (is169) DarkSurfaceVariant else DarkSurface)
                        .border(
                            BorderStroke(
                                if (is169) 2.dp else 1.dp,
                                if (is169) White else BorderColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onOrientationChanged(StreamOrientation.HORIZONTAL_16_9) }
                        .padding(12.dp)
                        .testTag("opt_aspect_16_9"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (is169) White else PureGray)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "16:9 Landscape",
                            fontSize = 13.sp,
                            fontWeight = if (is169) FontWeight.Bold else FontWeight.Normal,
                            color = if (is169) White else TextSecondary
                        )
                        Text(
                            text = "Standard / Gaming",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                // 9:16 Vertical Option
                val is916 = orientation == StreamOrientation.VERTICAL_9_16
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (is916) DarkSurfaceVariant else DarkSurface)
                        .border(
                            BorderStroke(
                                if (is916) 2.dp else 1.dp,
                                if (is916) White else BorderColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onOrientationChanged(StreamOrientation.VERTICAL_9_16) }
                        .padding(12.dp)
                        .testTag("opt_aspect_9_16"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (is916) White else PureGray)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "9:16 Vertical",
                            fontSize = 13.sp,
                            fontWeight = if (is916) FontWeight.Bold else FontWeight.Normal,
                            color = if (is916) White else TextSecondary
                        )
                        Text(
                            text = "Shorts & Mobile",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
