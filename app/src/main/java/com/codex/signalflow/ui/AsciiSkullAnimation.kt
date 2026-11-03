package com.codex.signalflow.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Cranium (top part of the skull, completely static)
private val SKULL_CRANIUM = """
         ,--.
       ,g§§§§Sk.
     ,d§§§§§§§§§§k.
    ,?^?§7°'  '?§SS§§L.
   ,?   §SL._  ,d§iiS§§§SL
  j§Su%S§S§§§§§?:iiS§§§Sb
 :?°?^4§S§§"^°?§SL:iiS§SI:
 :'   '/'?§'.   '?LiS§§SI:
 '       ,        ?kiSSI?
       :.      §k  _  '?Si7
     .,_,-     i§S%7- :i?'
""".trimIndent().replace('§', '$')

// Jaw (lower mandible, articulates up and down when laughing)
private val SKULL_JAW = """
    ?%uS%uo.d§§?' -?°'
    S§§§§§§§§§i
    -?§§S?
""".trimIndent().replace('§', '$')

/**
 * Natural laughing ASCII Skull: mouth opens and closes with varying rhythmic frequency.
 */
@Composable
fun AsciiSkullCard(
    modifier: Modifier = Modifier,
    title: String = "DEDSEC: SKULL.TXT",
) {
    val cursor = rememberBlinkingCursor()
    val transition = rememberInfiniteTransition(label = "ascii_laugh_loop")

    // Master 3600ms loop for organic varying laughter cadence
    val timeMs by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "laugh_time_ms",
    )

    // Gap specifies how many empty lines are between cranium and jaw:
    // 0 = mouth closed, 1 = mouth half open, 2 = mouth wide open.
    val (gap, isLaughing) = when (timeMs) {
        in 0f..600f -> Pair(0, false)       // Pause (closed)
        in 600f..750f -> Pair(1, true)      // Chuckle 1 (open)
        in 750f..900f -> Pair(0, true)      // (close)
        in 900f..1050f -> Pair(1, true)     // Chuckle 2 (open)
        in 1050f..1200f -> Pair(0, true)    // (close)
        in 1200f..1350f -> Pair(1, true)    // Chuckle 3 (open)
        in 1350f..1600f -> Pair(0, false)   // Short breath (closed)
        in 1600f..1900f -> Pair(2, true)    // Hearty laugh 1 (wide open!)
        in 1900f..2050f -> Pair(1, true)    // (mid close)
        in 2050f..2400f -> Pair(2, true)    // Hearty laugh 2 (wide open!)
        in 2400f..2600f -> Pair(0, true)    // (close)
        in 2600f..2800f -> Pair(1, true)    // Chuckle 4 (open)
        else -> Pair(0, false)              // Resting pause (closed)
    }

    // Keep total line count constant (16 lines) so the card never jumps in height
    val skullText = buildString {
        append(SKULL_CRANIUM)
        append("\n")
        repeat(gap) {
            append("\n")
        }
        append(SKULL_JAW)
        repeat(2 - gap) {
            append("\n")
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CutPanelShape)
            .background(Color(0xFF06090E))
            .border(1.dp, if (isLaughing) Color.White else Color(0xFF222B38), CutPanelShape)
            .techCornerMarks(markColor = if (isLaughing) Color.White else Color(0x66FFFFFF))
            .scanlineOverlay(lineColor = Color(0x18FFFFFF), stepPx = 4f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isLaughing) Color.White else Color(0xFF00FF66), CutSmallShape),
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = if (isLaughing) "СМЕХ" else "ОЖИДАНИЕ",
                    color = if (isLaughing) Color.White else Color(0xFF7A889B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ASCII Skull Art Display (smoothly opens and closes mouth)
            Text(
                text = skullText,
                color = Color(0xFFD4E0F0),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            // Emacs / Terminal Bottom Status Bar from screenshot: -UUU:**--F1   skull.txt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFD6DFEB), CutSmallShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "-UUU:**--F1   skull.txt$cursor",
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "100%   L14",
                        color = Color(0xFF333333),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
