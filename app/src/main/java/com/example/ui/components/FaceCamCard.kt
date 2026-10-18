package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stream.model.FaceCamShape
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PureGray
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.White

@Composable
fun FaceCamCard(
    isFaceCamEnabled: Boolean,
    faceCamShape: FaceCamShape,
    faceCamSizeDp: Int,
    onFaceCamEnabledChanged: (Boolean) -> Unit,
    onFaceCamShapeChanged: (FaceCamShape) -> Unit,
    onFaceCamSizeChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasOverlayPermission = Settings.canDrawOverlays(context)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Face Cam Overlay",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Clean floating camera with auto-hiding controls",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = isFaceCamEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasOverlayPermission) {
                            requestOverlayPermission(context)
                        } else {
                            onFaceCamEnabledChanged(enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
                        checkedTrackColor = PureGray,
                        uncheckedThumbColor = PureGray,
                        uncheckedTrackColor = DarkSurface
                    ),
                    modifier = Modifier.testTag("switch_facecam")
                )
            }

            if (!hasOverlayPermission) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { requestOverlayPermission(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, White),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
                ) {
                    Text("Grant Floating Window Permission", fontSize = 12.sp)
                }
            }

            if (isFaceCamEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Shape Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FaceCamShape.values().forEach { shape ->
                        val isSelected = shape == faceCamShape
                        OutlinedButton(
                            onClick = { onFaceCamShapeChanged(shape) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_facecam_${shape.name}"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) White else BorderColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) DarkSurfaceVariant else DarkSurface,
                                contentColor = if (isSelected) White else TextSecondary
                            )
                        ) {
                            Text(
                                text = if (shape == FaceCamShape.CIRCLE) "Circle" else "Square",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Camera Size", fontSize = 13.sp, color = TextPrimary)
                    Text("${faceCamSizeDp}dp", fontSize = 13.sp, color = White, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = faceCamSizeDp.toFloat(),
                    onValueChange = { onFaceCamSizeChanged(it.toInt()) },
                    valueRange = 80f..220f,
                    colors = SliderDefaults.colors(
                        thumbColor = White,
                        activeTrackColor = White,
                        inactiveTrackColor = BorderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_facecam_size")
                )
            }
        }
    }
}

private fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
