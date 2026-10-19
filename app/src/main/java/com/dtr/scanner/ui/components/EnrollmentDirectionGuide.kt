package com.dtr.scanner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dtr.scanner.R
import com.dtr.scanner.face.EnrollmentGuideState
import com.dtr.scanner.face.EnrollmentGuideStep
import com.dtr.scanner.face.EnrollmentStepStatus
import com.dtr.scanner.ui.theme.SkyAccent

@Composable
fun EnrollmentDirectionGuide(
    state: EnrollmentGuideState?,
    modifier: Modifier = Modifier,
) {
    if (state == null || state.livenessComplete) return

    val pulse = rememberInfiniteTransition(label = "arrowPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrowPulseScale",
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val showingCelebration = state.celebrateStep != null
        val directionStep = if (showingCelebration) null else state.activeStep
        if (directionStep != null && directionStep != EnrollmentGuideStep.BLINK) {
            EdgeDirectionArrow(step = directionStep, pulseScale = pulseScale)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            EnrollmentStepProgressRow(steps = state.steps)

            AnimatedContent(
                targetState = when {
                    showingCelebration -> "celebrate-${state.celebrateStep}"
                    directionStep != null -> "dir-$directionStep"
                    else -> "none"
                },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "enrollmentGuideMain",
            ) { target ->
                when {
                    target.startsWith("celebrate") && state.celebrateStep != null -> {
                        CelebrationContent(step = state.celebrateStep)
                    }

                    target.startsWith("dir") && directionStep != null -> {
                        DirectionContent(step = directionStep, pulseScale = pulseScale)
                    }
                }
            }
        }
    }
}

@Composable
private fun EdgeDirectionArrow(
    step: EnrollmentGuideStep,
    pulseScale: Float,
) {
    val alignment = when (step) {
        EnrollmentGuideStep.TURN_LEFT -> Alignment.CenterStart
        EnrollmentGuideStep.TURN_RIGHT -> Alignment.CenterEnd
        EnrollmentGuideStep.NOD_UP -> Alignment.TopCenter
        EnrollmentGuideStep.NOD_DOWN -> Alignment.BottomCenter
        EnrollmentGuideStep.BLINK -> Alignment.Center
    }
    val padding = Modifier.padding(
        start = if (step == EnrollmentGuideStep.TURN_LEFT) 20.dp else 0.dp,
        end = if (step == EnrollmentGuideStep.TURN_RIGHT) 20.dp else 0.dp,
        top = if (step == EnrollmentGuideStep.NOD_UP) 72.dp else 0.dp,
        bottom = if (step == EnrollmentGuideStep.NOD_DOWN) 24.dp else 0.dp,
    )

    Box(
        modifier = Modifier.fillMaxSize().then(padding),
        contentAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
                .background(SkyAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = step.directionIcon(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@Composable
private fun EnrollmentStepProgressRow(steps: List<EnrollmentGuideState.EnrollmentStepState>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (stepState in steps) {
            val done = stepState.status == EnrollmentStepStatus.DONE
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = when (stepState.status) {
                            EnrollmentStepStatus.DONE -> SkyAccent.copy(alpha = 0.9f)
                            EnrollmentStepStatus.ACTIVE -> Color.White.copy(alpha = 0.35f)
                            EnrollmentStepStatus.PENDING -> Color.White.copy(alpha = 0.12f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(
                        imageVector = stepState.step.smallIcon(),
                        contentDescription = null,
                        tint = Color.White.copy(
                            alpha = if (stepState.status == EnrollmentStepStatus.ACTIVE) 1f else 0.45f,
                        ),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CelebrationContent(step: EnrollmentGuideStep) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(SkyAccent.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = SkyAccent,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            text = stringResource(R.string.enrollment_guide_good),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = step.nextPromptText(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DirectionContent(
    step: EnrollmentGuideStep,
    pulseScale: Float,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (step == EnrollmentGuideStep.BLINK) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(52.dp)
                    .scale(pulseScale),
            )
        }
        Text(
            text = step.instruction(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EnrollmentGuideStep.instruction(): String = when (this) {
    EnrollmentGuideStep.BLINK -> stringResource(R.string.enrollment_guide_blink)
    EnrollmentGuideStep.TURN_LEFT -> stringResource(R.string.enrollment_guide_turn_left)
    EnrollmentGuideStep.TURN_RIGHT -> stringResource(R.string.enrollment_guide_turn_right)
    EnrollmentGuideStep.NOD_UP -> stringResource(R.string.enrollment_guide_nod_up)
    EnrollmentGuideStep.NOD_DOWN -> stringResource(R.string.enrollment_guide_nod_down)
}

@Composable
private fun EnrollmentGuideStep.nextPromptText(): String = when (this) {
    EnrollmentGuideStep.BLINK -> stringResource(R.string.enrollment_guide_next_left)
    EnrollmentGuideStep.TURN_LEFT -> stringResource(R.string.enrollment_guide_next_right)
    EnrollmentGuideStep.TURN_RIGHT -> stringResource(R.string.enrollment_guide_next_up)
    EnrollmentGuideStep.NOD_UP -> stringResource(R.string.enrollment_guide_next_down)
    EnrollmentGuideStep.NOD_DOWN -> stringResource(R.string.enrollment_guide_next_samples)
}

private fun EnrollmentGuideStep.directionIcon(): ImageVector = when (this) {
    EnrollmentGuideStep.BLINK -> Icons.Default.Visibility
    EnrollmentGuideStep.TURN_LEFT -> Icons.Default.ArrowBack
    EnrollmentGuideStep.TURN_RIGHT -> Icons.Default.ArrowForward
    EnrollmentGuideStep.NOD_UP -> Icons.Default.ArrowUpward
    EnrollmentGuideStep.NOD_DOWN -> Icons.Default.ArrowDownward
}

private fun EnrollmentGuideStep.smallIcon(): ImageVector = directionIcon()
