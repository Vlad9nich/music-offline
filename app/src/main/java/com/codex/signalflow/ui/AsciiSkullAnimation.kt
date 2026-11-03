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

// Cranium: 1:1 Matrix replica of user's uploaded reference (curved cranial vault looking upward, dark eye sockets)
// Exactly 27 characters wide per line
private val SKULL_CRANIUM = """
|         .:::::::.
|      .:iiiiIiIiiii:.
|    .:iiiiiiiiiiiiiii:.
|   :iii::i::i:i::i::iii:
|  :Iii::..ii:.:ii..::iiI:
| .iii      i: :i      iii.
| .:i.     .i   i.     .i:.
| .ii.  .::i:   :i::.  .ii.
| ::iiiiiiii..:..iiiiiiii::
| ::.:::iIiiiiiiiiiIi:::.::
| :i.   :iiiiiiiiiii:   .i:
| .:ii: .iiiiiiiiiii. :ii:.
|  .i::.iii:. . .:iii.::i.
""".trimMargin("|").trim('\n')

// Mandible / Jaw: Screaming open mouth from user reference, opens & articulates downward when laughing
private fun getSkullJaw(cursorChar: String) = """
|   ::i.::         ::.i::
|    :I.             .I:
|   .:ii             ii:.
|    :ii:           :ii:
|    :iiii.       .iiii:
|     .iiii.......iiii.
|       :iiIii${cursorChar}iIii:
|        :iiiiiiiii:
|         .iIIIIIi.
|          .:::::.
""".trimMargin("|").trim('\n')

/**
 * Natural laughing DedSec ASCII Skull (Direct 1:1 implementation of User's Uploaded Reference):
 * - Curved, anatomically upward-angled cranial vault (replaces the old flat/blunt top).
 * - Upward-looking dark eye sockets with sharp matrix cheekbones.
 * - Iconic wide screaming/laughing open mouth that articulates dynamically downward.
 * - Authentic GNU Emacs status bar: -UUU:**--F1 skull.txt.
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
                6f at 1120        // Chuckle 1 (mouth opens)
                0f at 1240        // Closes
                7f at 1360        // Chuckle 2
                0f at 1480        // Closes
                8f at 1600        // Chuckle 3
                0f at 1720        // Closes
                0f at 2000        // Inhale breath
                16f at 2180       // Big laugh burst (wide open screaming mouth!)
                11f at 2280
                16f at 2400       // Hearty laugh burst
                9f at 2520
                14f at 2640       // Tremor laugh
                5f at 2780
                0f at 2920        // Return to resting
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
    val cursorChar = if (cursorBlink > 0.5f) "█" else "i"

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
                    .height(310.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.wrapContentSize(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    // Cranium (stationary upward-looking dome and eye sockets)
                    Text(
                        text = SKULL_CRANIUM,
                        color = Color(0xFFD6E2F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        softWrap = false,
                    )

                    // Articulated jaw gap (opens and closes when laughing)
                    Spacer(modifier = Modifier.height(jawOffset.dp))

                    // Jaw (wide open screaming/laughing mouth cavity from reference)
                    Text(
                        text = getSkullJaw(cursorChar),
                        color = Color(0xFFD6E2F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        softWrap = false,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Emacs / Terminal Bottom Status Bar: -UUU:**--F1   skull.txt
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
