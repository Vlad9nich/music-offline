package com.codex.signalflow.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.signalflow.R
import kotlinx.coroutines.delay

// Cranium: 1:1 Matrix replica of user's uploaded reference (curved cranial vault looking upward, dark eye sockets)
// Exactly 45 characters wide per line, perfectly symmetrical & centered
private val ASCII_CRANIUM_45 = """
|                 ............                
|              ..:iIIIIIIIIIII:..             
|           :::IIIIIIIIIIIIIIiiII::.          
|         .iIIIIIIIiIIIIiiIIiIiIII1i:         
|       .i:iiiiIIIiiiIIIiiiiiIiiIiiIIii.      
|      :iIii:::iiIiiIIIi:iiiiiiii:iIiiii:     
|     :IIIiIIIIIiIIIiiiiiIiii:ii:iiIIiiIIi    
|    :iIIIIii:.:.:IIii::.:iIi..::..::iIIII:   
|   .iIIIi:       ::iI.   ii:        :iIIII.  
|  :iiIII           ii   .Ii          .:IIi:  
|  .:iIi:          :I.   ..i:          .i:i:  
|   iIii:        :i1i     .i::::       .iiii  
|  :iiIi:  .Ii:iiIII        ::iiii:.   :ii::. 
|  ii:iiiIIIIIIIIIII.. i.  .:iiiIIiiiiiiii::i 
| .ii:iiIIIIIIIIiIIiIIiIiiiIiIi:iiiiiiiii:I::.
| :iI:.::i:::IIIIIIiiiiIiiiiiiiiiiI:..::.:i::.
|  iIi:      .iIiiIiIIiIiiiiiiiiIi.     .:i:: 
|  iiII::::   .iIIIiiiiiiiii::iIi.   .::Iii:: 
|   ::iIIIi   :IiIIiiiiiiiii::iiIi   :iiii::  
|    iiiiiI..:iIiIi:i::I.::i:iiiII  ::iii::   
""".trimMargin("|").trim('\n')

// Mandible / Jaw: Screaming open mouth from user reference, opens & articulates downward when laughing
// Exactly 45 characters wide per line, perfectly symmetrical & centered
private fun getAsciiJaw45(cursorChar: String) = """
|    iiii:I: :i1iI.          :iIII.:Iiii:::   
|    ::iiIIi iii               :ii..iii::..   
|       .Iii  .                  .  iii:      
|      .:III                        :ii.      
|      :iIII:                     ..::ii:     
|      .IIIII.                    .ii::i:     
|      .:IIIII.                  .IIiiI.      
|       .IIIIiiI:              .IIIIIII       
|        .iIiiiii:            :IIIIIIi        
|         ..Iiiiiii..iiiii:::IiIIiiI:         
|           .:iIiIIIIIIIiiIIIIiIIii.          
|              iIIIiiIiiiiiIIIIIi..           
|              ::IiiIIiIIIIIIIii.             
|                iIIIiiIIIIIII.               
|                 iIIIII${cursorChar}III:                
|                  ::i:i::i::                 
""".trimMargin("|").trim('\n')

/**
 * DedSec Glitch Skull Monitor (Direct 1:1 Implementation of User's Uploaded Reference):
 * - Authentic Watch Dogs phosphor scanline graphics with laughing / articulating jaw.
 * - Interactive: On tap, skull enters rapid jaw-chattering attack overdrive with blazing red eye sockets!
 * - Perfectly symmetrical, upward-looking cranial vault with sharp cheekbones and tapered chin.
 * - Articulated mandible that drops downward into a wide screaming / laughing open mouth.
 * - Interactive mode toggle: RASTER GLITCH (default photo replica) <-> ASCII MATRIX (45-col text).
 * - Authentic GNU Emacs status bar: -UUU:**--F1 skull.txt.
 */
@Composable
fun AsciiSkullCard(
    modifier: Modifier = Modifier,
    title: String = "skull.txt",
) {
    var isAsciiMode by remember { mutableStateOf(false) }
    var isAttacking by remember { mutableStateOf(false) }
    var attackTrigger by remember { mutableStateOf(0L) }
    val haptic = LocalHapticFeedback.current

    val transition = rememberInfiniteTransition(label = "skull_loop")

    // Timer for attack overdrive mode (chatter + red eyes for 2.6 seconds)
    LaunchedEffect(attackTrigger) {
        if (attackTrigger > 0L) {
            isAttacking = true
            delay(2600)
            isAttacking = false
        }
    }

    // Normal idle laughing jaw animation
    val normalJawOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                0f at 0           // Resting sinister grin
                0f at 1000
                5f at 1120        // Chuckle 1 (mouth opens)
                0f at 1240        // Closes
                7f at 1360        // Chuckle 2
                0f at 1480        // Closes
                9f at 1600        // Chuckle 3
                0f at 1720        // Closes
                0f at 2000        // Inhale breath
                18f at 2180       // Big laugh burst (wide open screaming mouth!)
                11f at 2280
                20f at 2400       // Hearty laugh burst
                9f at 2520
                15f at 2640       // Tremor laugh
                5f at 2780
                0f at 2920        // Return to resting
                0f at 4200        // Calm resting pause
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "normal_jaw_offset",
    )

    // Rapid chatter jaw animation triggered by user tap
    val rapidChatterOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 75, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rapid_chatter_offset",
    )

    // Active jaw offset depending on interactive state
    val activeJawOffset = if (isAttacking) rapidChatterOffset else normalJawOffset

    // Eye socket red pulsating glow animation
    val eyePulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 85, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eye_pulse",
    )

    // Glitch horizontal jitter offset for CRT / DedSec distortion
    val glitchShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = if (isAttacking) 800 else 3600
                if (isAttacking) {
                    0f at 0
                    4f at 100
                    (-4f) at 200
                    5f at 300
                    (-3f) at 400
                    0f at 500
                    (-4f) at 600
                    0f at 800
                } else {
                    0f at 0
                    0f at 2100
                    3f at 2150
                    (-3f) at 2200
                    0f at 2250
                    0f at 3200
                    (-2f) at 3240
                    0f at 3280
                    0f at 3600
                }
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "glitch_shift",
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
    val cursorChar = if (cursorBlink > 0.5f) "█" else "I"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CutPanelShape)
            .background(Color.Black)
            .border(
                width = 1.dp,
                color = if (isAttacking) Color(0xFFFF003C) else Color(0xFF1E2836),
                shape = CutPanelShape,
            )
            .techCornerMarks(markColor = if (isAttacking) Color(0xFFFF003C) else Color(0x66FFFFFF)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Interactive DedSec Skull Stage (Tap to trigger chatter & red eyes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clickable {
                        attackTrigger = System.currentTimeMillis()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Background Dot Grid (Replicating user's uploaded wallpaper)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 18.dp.toPx()
                    val dotRadius = 1.dp.toPx()
                    val cols = (size.width / step).toInt()
                    val rows = (size.height / step).toInt()
                    val dotColor = if (isAttacking) Color(0x28FF003C) else Color(0x18FFFFFF)
                    for (c in 0..cols) {
                        for (r in 0..rows) {
                            drawCircle(
                                color = dotColor,
                                radius = dotRadius,
                                center = Offset(c * step + (step / 2), r * step + (step / 2)),
                            )
                        }
                    }
                }

                if (!isAsciiMode) {
                    // 1) RASTER GLITCH MODE: 1:1 Scanline Digital Skull Rig
                    Box(
                        modifier = Modifier
                            .size(width = 220.dp, height = 300.dp)
                            .offset(x = glitchShift.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        // A) Mandible / Jaw: placed behind cranium, articulates downward on laugh / chatter
                        // Original ratio: width 455, height 300; resting Y offset = 148.dp
                        Image(
                            painter = painterResource(id = R.drawable.dedsec_skull_jaw),
                            contentDescription = "DedSec Jaw",
                            modifier = Modifier
                                .width(220.dp)
                                .height(145.dp)
                                .offset(y = (148f + activeJawOffset).dp),
                            contentScale = ContentScale.FillWidth,
                        )

                        // B) Cranium: placed at top (y = 0), cheekbones cover the descending jaw ramus
                        // Original ratio: width 455, height 410; scaled height = 198.dp
                        Image(
                            painter = painterResource(id = R.drawable.dedsec_skull_cranium),
                            contentDescription = "DedSec Cranium",
                            modifier = Modifier
                                .width(220.dp)
                                .height(198.dp),
                            contentScale = ContentScale.FillWidth,
                        )

                        // C) Red Burning Eye Sockets (Triggered on tap)
                        if (isAttacking) {
                            // Left eye socket center: x = 64.5.dp, y = 85.5.dp
                            val eyeGlowSize = (26f * eyePulse).dp
                            val eyeCoreSize = (7f * eyePulse).dp

                            Box(
                                modifier = Modifier
                                    .offset(x = 64.5.dp - (eyeGlowSize / 2), y = 85.5.dp - (eyeGlowSize / 2))
                                    .size(eyeGlowSize)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFFF003C),
                                                Color(0xCCFF0022),
                                                Color(0x44FF0011),
                                                Color.Transparent,
                                            ),
                                        ),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(eyeCoreSize)
                                        .background(Color(0xFFFFE0E0), CircleShape),
                                )
                            }

                            // Right eye socket center: x = 154.2.dp, y = 85.5.dp
                            Box(
                                modifier = Modifier
                                    .offset(x = 154.2.dp - (eyeGlowSize / 2), y = 85.5.dp - (eyeGlowSize / 2))
                                    .size(eyeGlowSize)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFFF003C),
                                                Color(0xCCFF0022),
                                                Color(0x44FF0011),
                                                Color.Transparent,
                                            ),
                                        ),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(eyeCoreSize)
                                        .background(Color(0xFFFFE0E0), CircleShape),
                                )
                            }
                        }
                    }
                } else {
                    // 2) ASCII MATRIX MODE: 45-character centered text grid
                    val asciiTextColor = if (isAttacking) Color(0xFFFF2233) else Color(0xFFD6E2F0)
                    Column(
                        modifier = Modifier
                            .wrapContentSize()
                            .offset(x = glitchShift.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Cranium
                        Text(
                            text = ASCII_CRANIUM_45,
                            color = asciiTextColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            softWrap = false,
                        )

                        // Articulated jaw gap
                        Spacer(modifier = Modifier.height((activeJawOffset * 0.7f).dp))

                        // Jaw
                        Text(
                            text = getAsciiJaw45(cursorChar),
                            color = asciiTextColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            softWrap = false,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Emacs / Terminal Bottom Status Bar: Interactive mode & status
            val statusBg = if (isAttacking) Color(0xFFFF003C) else Color(0xFF9EACB8)
            val statusFg = if (isAttacking) Color.White else Color.Black
            val statusLabel = if (isAttacking) "! DEDSEC OVERDRIVE !" else "-UUU:**--F1   $title"
            val badgeLabel = if (isAttacking) "[ CHATTER ]" else if (isAsciiMode) "[ASCII]" else "[GLITCH]"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusBg)
                    .clickable { isAsciiMode = !isAsciiMode }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = statusLabel,
                        color = statusFg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = badgeLabel,
                        color = if (isAttacking) Color.Yellow else Color(0xFF003311),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
