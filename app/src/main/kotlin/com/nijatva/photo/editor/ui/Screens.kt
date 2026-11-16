package com.nijatva.photo.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nijatva.photo.editor.R

@Composable
fun ApertureMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.15f
        val inset = stroke / 2f
        val brush = Brush.sweepGradient(
            listOf(NijatvaTeal, NijatvaBlue, NijatvaViolet, NijatvaPink, NijatvaTeal)
        )
        drawArc(
            brush = brush,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) NijatvaTeal.copy(alpha = 0.16f) else Surface2,
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (selected) NijatvaTeal else Color(0xFF2A2A2E),
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (selected) NijatvaTeal else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun HomeScreen(onOpenPhoto: () -> Unit, onAbout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(96.dp))
        ApertureMark(Modifier.size(132.dp))
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = TextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.tagline),
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(56.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(NijatvaTeal, RoundedCornerShape(16.dp))
                .clickable { onOpenPhoto() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.open_photo),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAbout) {
            Text(stringResource(R.string.about), color = TextSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(
                text = stringResource(R.string.tip_title),
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            TipLine(stringResource(R.string.tip_one))
            TipLine(stringResource(R.string.tip_two))
            TipLine(stringResource(R.string.tip_three))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.privacy_note),
            color = Color(0xFF6E6E73),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 28.dp)
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back), color = NijatvaTeal)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.about),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ApertureMark(Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.app_name), color = TextPrimary, fontSize = 22.sp)
                Text("v1.2.0", color = TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.about_body),
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.attribution),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.privacy_note),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Kotlin · Jetpack Compose · Material 3 · AMOLED",
                color = Color(0xFF4A4A4E),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TipLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text("•", color = NijatvaTeal, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
