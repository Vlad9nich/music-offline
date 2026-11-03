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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Exact ASCII art transcribed from user's terminal screenshot (skull.txt)
// Uses § placeholder replaced with $ at runtime to prevent Kotlin string template errors.
private val SKULL_FRAME_IDLE = """
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
    ?%uS%uo.d§§?' -?°'
    S§§§§§§§§§i
    -?§§S?
""".trimIndent().replace('§', '$')

private val SKULL_FRAME_LAUGH_1 = """
         ,--.
       ,g§§§§Sk.
     ,d§§§§§§§§§§k.
    ,?^?§7°'  '?§SS§§L.
   ,?   §SL._  ,d§iiS§§§SL
  j§Su%S§S§§§§§?:iiS§§§Sb
 :?°?^4§S§§"^°?§SL:iiS§SI:
 :'   '/'?§'.   '?LiS§§SI:
 '  [ HA! HA! ]   ?kiSSI?
       :.   \___/   '?Si7
     .,_,-  |   | :i?'
    ?%uS%uo.d§§?' -?°'
      S§§§§§§§§§i
      -?§§S?
""".trimIndent().replace('§', '$')

private val SKULL_FRAME_LAUGH_2 = """
         ,--.
       ,g§§§§Sk.
     ,d§§§§§§§§§§k.
    ,?^?§7°'  '?§SS§§L.
   ,?   §SL._  ,d§iiS§§§SL
  j§Su%S§S§§§§§?:iiS§§§Sb
 :?°?^4§S§§"^°?§SL:iiS§SI:
 :'   '/'?§'.   '?LiS§§SI:
 ' [ DEDSEC:LAUGH ] ?kiSSI?
       :.   \_____/ '?Si7
     .,_,-  |     | :i?'
    ?%uS%uo.d§§?' -?°'
        S§§§§§§§§§i
        -?§§S?
""".trimIndent().replace('§', '$')

private val SKULL_FRAME_GLITCH = """
        _ ,--. _
       ,g§§§§#k.
     ,,d§§§§§##§§k.
    ,,?^?§7°'  '?§SS§§L.
   ,??   §SL._  ,d§iiS§§§SL
  jj§Su%S§S§§§§§?:iiS§§§Sb
 :??°?^4§S§§"^°?§SL:iiS§SI:
 :'    '/'?§'.   '?LiS§§SI:
 ''       ,,       ?kiSSI?
       ::.     §k  _  '?Si7
     ..,_,-    i§S%7- :i?'
    ??%uS%uo.d§§?' -?°'
    SS§§§§§§§§§i
    --?§§S?
""".trimIndent().replace('§', '$')

/**
 * Animated Laughing ASCII Skull from Watch Dogs 2 / DedSec terminal.
 */
@Composable
fun AsciiSkullCard(
    modifier: Modifier = Modifier,
    title: String = "DEDSEC: SKULL.TXT",
) {
    val cursor = rememberBlinkingCursor()
    val transition = rememberInfiniteTransition(label = "ascii_laugh")
    
    // Animate laugh cycle (0 to 100)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "laugh_phase",
    )

    // Jitter for glitch frame
    val glitchJitter by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glitch_jitter",
    )

    val currentFrame = when {
        phase < 35f -> SKULL_FRAME_IDLE
        phase < 45f -> SKULL_FRAME_LAUGH_1
        phase < 55f -> SKULL_FRAME_LAUGH_2
        phase < 65f -> SKULL_FRAME_LAUGH_1
        phase < 75f -> SKULL_FRAME_LAUGH_2
        phase < 82f -> SKULL_FRAME_GLITCH
        else -> SKULL_FRAME_IDLE
    }

    val isLaughing = phase in 35f..75f
    val isGlitching = phase in 80f..84f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CutPanelShape)
            .background(Color(0xFF06090E))
            .border(1.dp, if (isLaughing) Color(0xFFFFFFFF) else Color(0xFF222B38), CutPanelShape)
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
                    text = if (isLaughing) "СМЕХ" else "МОНИТОРИНГ",
                    color = if (isLaughing) Color.White else Color(0xFF7A889B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ASCII Skull Art Display
            Text(
                text = currentFrame,
                color = if (isGlitching) Color(0xFFE2EAF4) else Color(0xFFD0DCED),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = if (isGlitching) glitchJitter * 4f else 0f
                        scaleX = if (isLaughing) 1.03f else 1f
                        scaleY = if (isLaughing) 1.03f else 1f
                    },
            )

            Spacer(Modifier.height(10.dp))

            // Emacs / Terminal Bottom Status Bar from reference screenshot: -UUU:**--F1   skull.txt
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
