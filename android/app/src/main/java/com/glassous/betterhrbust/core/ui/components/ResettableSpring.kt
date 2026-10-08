package com.glassous.betterhrbust.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 弹簧参数：与 Compose `spring()` 的 stiffness / dampingRatio 语义一致，
 * 但由 [ResettableSpring] 自行积分，可逐帧重定向目标值。
 */
data class DockSpringSpec(val stiffness: Float, val dampingRatio: Float)

/**
 * 可逐帧重定向的弹簧值。
 *
 * 采用常驻帧循环按半隐式欧拉法积分，每帧读取最新的 [target]，
 * 拖动时数值始终紧跟手指，且支持物理动量衰减。
 *
 * 帧循环只在 [running] 为 true 时运行（由 `snapshotFlow` 驱动），静止时不占用帧。
 */
@Stable
class ResettableSpring(initialValue: Float) {

    /** 当前值。绘制期读取（`graphicsLayer` / `drawWithContent`）不会触发重组。 */
    var value by mutableFloatStateOf(initialValue)
        private set

    /** 当前目标值。 */
    var target by mutableFloatStateOf(initialValue)
        private set

    /** 当前速度（单位/秒），用于 lens 的"果冻"拉伸。 */
    var velocity by mutableFloatStateOf(0f)
        private set

    /** 是否正在运动（帧循环的开关）。 */
    internal var running by mutableStateOf(false)
        private set

    private var stiffness = 1000f
    private var dampingRatio = 1f

    /** 立即落到某值并停止运动。 */
    fun jumpTo(newValue: Float) {
        value = newValue
        target = newValue
        velocity = 0f
        running = false
    }

    /**
     * 弹向 [targetValue]。数值已在目标附近且静止时直接落位，不启动帧循环。
     * 运动过程中可反复调用（例如拖拽时逐帧调用），只更新目标与弹簧参数。
     */
    fun springTo(targetValue: Float, spec: DockSpringSpec) {
        val alreadySettled = !running &&
            abs(targetValue - value) <= RestThreshold &&
            abs(velocity) <= RestVelocityThreshold
        if (alreadySettled) {
            jumpTo(targetValue)
            return
        }
        target = targetValue
        stiffness = spec.stiffness
        dampingRatio = spec.dampingRatio
        if (!running) running = true
    }

    /** 由帧循环调用：按 elapsed 时长推进弹簧（拆成小步长保证高刚度下数值稳定）。 */
    internal fun tick(elapsedNanos: Long) {
        var remaining = (elapsedNanos / 1_000_000_000f).coerceIn(0f, MaxFrameSeconds)
        while (remaining > 0f) {
            val step = if (remaining > SubStepSeconds) SubStepSeconds else remaining
            integrate(step)
            remaining -= step
        }
        if (abs(target - value) <= RestThreshold && abs(velocity) <= RestVelocityThreshold) {
            value = target
            velocity = 0f
            running = false
        }
    }

    private fun integrate(dt: Float) {
        val k = stiffness
        val c = 2f * dampingRatio * sqrt(k)
        val acceleration = -k * (value - target) - c * velocity
        val nextVelocity = velocity + acceleration * dt
        value += nextVelocity * dt
        velocity = nextVelocity
    }

    private companion object {
        const val SubStepSeconds = 0.004f
        const val MaxFrameSeconds = 0.05f
        const val RestThreshold = 0.0005f
        const val RestVelocityThreshold = 0.02f
    }
}

/** 记住一个 [ResettableSpring]，并挂上仅在运动期间运行的帧循环。 */
@Composable
fun rememberResettableSpring(initialValue: Float): ResettableSpring {
    val spring = remember { ResettableSpring(initialValue) }
    LaunchedEffect(spring) {
        snapshotFlow { spring.running }.collectLatest { running ->
            if (!running) return@collectLatest
            var lastFrameNanos = 0L
            while (spring.running) {
                withFrameNanos { now ->
                    if (lastFrameNanos != 0L) spring.tick(now - lastFrameNanos)
                    lastFrameNanos = now
                }
            }
        }
    }
    return spring
}
