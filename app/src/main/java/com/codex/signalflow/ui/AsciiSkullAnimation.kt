package com.codex.signalflow.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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

// Cranium (top part of the skull - stationary, exact transcription of skull.txt)
private val SKULL_CRANIUM = """
|          --
|        ,gS§§§Sk.
|      ,d§§§§§§§§§§k.
|     ,?^?§7°' `'?§SS§§L.
|    ,?  §SL._  ,d§iIS§§§SL
|   j§Su%§S§§§§§?:iIS§§§Sb
|  :?º?^4§S§§"º?§SL:iIS§SI:
|  :'   '/`?§'.  `?LiS§§SI:
|  '     ,         ?kiSSI?
|        :.     §k ¯ `?Si7
|        .,_,_  i§S%7·:i?'
""".trimMargin("|").trim('\n').replace('§', '$')

// Jaw (lower mandible - articulates smoothly downward when laughing)
// Cursor '█' blinks at line 11 between ?%uS%uo and d$$?'·?º` as shown in Emacs editor
private fun getSkullJaw(cursorChar: String) = """
|      ?%uS%uo${cursorChar}d§§?'·?º`
|      S§§§§§§§§§i
|     ·?§§S?
""".trimMargin("|").trim('\n').replace('§', '$')

/**
 * Natural laughing DedSec ASCII Skull:
 * Exact pixel-accurate transcription of the iconic `skull.txt` terminal buffer.
 * Simple, organic laughing animation: mouth opens and closes with varying rhythmic frequency.
 */
@Composable
fun AsciiSkullCard(
    modifier: Modifier = Modifier,
    title: String = "skull.txt",
) {
    val transition = rememberInfiniteTransition(label = "skull_laugh_loop")

    // Animated jaw offset (dp) simulating natural varied laughter
    val jawOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                0f at 0           // Resting (closed)
                0f at 1000
                6f at 1120        // Quick chuckle 1
                0f at 1240
                7f at 1360        // Quick chuckle 2
                0f at 1480
                8f at 1600        // Chuckle 3
                0f at 1720
                0f at 2000        // Inhale pause
                14f at 2180       // Big laugh burst (wide open)
                10f at 2280
                15f at 2400       // Hearty laugh
                8f at 2520
                13f at 2640       // Laugh tremor
                5f at 2780
                0f at 2920        // Close
                0f at 4200        // Calm resting pause
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "jaw_offset",
    )

    // Emacs text editor blinking block cursor
    val cursorBlink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_blink",
    )
    val cursorChar = if (cursorBlink > 0.5f) "█" else " "

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CutPanelShape)
            .background(Color.Black)
            .border(1.dp, Color(0xFF1E2836), CutPanelShape)
            .techCornerMarks(markColor = Color(0x66FFFFFF)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ASCII Skull Container (centered horizontally, text left-aligned to preserve ASCII grid)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.wrapContentSize(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    // Cranium (stationary)
                    Text(
                        text = SKULL_CRANIUM,
                        color = Color(0xFFD6E2F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        softWrap = false,
                    )

                    // Articulated jaw gap (opens and closes when laughing)
                    Spacer(modifier = Modifier.height(jawOffset.dp))

                    // Jaw with Emacs text cursor
                    Text(
                        text = getSkullJaw(cursorChar),
                        color = Color(0xFFD6E2F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        softWrap = false,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Emacs / Terminal Bottom Status Bar from reference screenshot: -UUU:**--F1   skull.txt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF9EACB8))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "-UUU:**--F1   $title",
                    color = Color.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
