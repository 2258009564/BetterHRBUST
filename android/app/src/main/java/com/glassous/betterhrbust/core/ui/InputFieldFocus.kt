package com.glassous.betterhrbust.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

/** 全局统一的输入框圆角：24dp 大圆角，所有 TextField / OutlinedTextField 共用。 */
val AppTextFieldShape = RoundedCornerShape(24.dp)

/**
 * 已挂载输入框在根坐标系下的触摸范围。
 *
 * 仅由 UI 线程读写；[clearFocusOnTapOutside] 借助它区分"点击输入框自身"与"点击其他区域"。
 */
private object InputFieldHitAreas {
    private val areas = mutableMapOf<Any, Rect>()

    fun put(key: Any, area: Rect) {
        areas[key] = area
    }

    fun remove(key: Any) {
        areas.remove(key)
    }

    fun contains(position: Offset): Boolean = areas.values.any { it.contains(position) }
}

/**
 * 把当前输入框登记进全局触摸范围表。
 *
 * 挂在每个 TextField / OutlinedTextField 的 modifier 上：
 * 点击落在任一已登记输入框范围内时，[clearFocusOnTapOutside] 不会清除焦点，
 * 避免"点击输入框自身被判定为点击了外部"导致键盘闪烁。
 */
fun Modifier.registerInputField(): Modifier = composed {
    val key = remember { Any() }
    DisposableEffect(key) {
        onDispose { InputFieldHitAreas.remove(key) }
    }
    onGloballyPositioned { coordinates ->
        InputFieldHitAreas.put(key, coordinates.boundsInRoot())
    }
}

/**
 * 点击本布局内任意非输入框区域（空白区域、按钮、顶部标题、其他组件等）时
 * 清除输入焦点并收起键盘；点击本身落在输入框范围内时保持焦点不变。
 *
 * 挂在页面最外层容器上即可覆盖整页；页面内的输入框需配合 [registerInputField] 登记范围。
 */
fun Modifier.clearFocusOnTapOutside(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val originInRoot = remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { originInRoot.value = it.positionInRoot() }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // 在 Initial 阶段观察按下事件：即使子组件（按钮等）随后消费了点击，
                    // 也能保证"点击其他组件"时清除焦点。
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Press) continue
                    val position = event.changes.firstOrNull()?.position ?: continue
                    if (!InputFieldHitAreas.contains(position + originInRoot.value)) {
                        focusManager.clearFocus()
                    }
                }
            }
        }
}
