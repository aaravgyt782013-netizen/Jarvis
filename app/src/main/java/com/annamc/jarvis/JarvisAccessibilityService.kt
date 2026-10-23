package com.annamc.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Optional user-enabled accessibility bridge. Android requires the user to enable it manually.
 * It only implements explicit, supported commands; it does not grant unrestricted device access.
 */
class JarvisAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun execute(command: String): String? {
        val q = command.trim()
        val lower = q.lowercase()
        return when {
            lower in setOf("go back", "back", "press back", "navigate back") ->
                if (performGlobalAction(GLOBAL_ACTION_BACK)) "Going back." else "Android could not go back."
            lower in setOf("go home", "home screen", "press home", "go to home screen") ->
                if (performGlobalAction(GLOBAL_ACTION_HOME)) "Going to the home screen." else "Android could not go home."
            lower in setOf("show notifications", "open notifications", "notifications") ->
                if (performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) "Opening notifications." else "Android could not open notifications."
            lower in setOf("open quick settings", "quick settings") ->
                if (performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) "Opening Quick Settings." else "Android could not open Quick Settings."
            lower in setOf("scroll down", "scroll page down", "swipe up") -> scroll(true)
            lower in setOf("scroll up", "scroll page up", "swipe down") -> scroll(false)
            lower == "tap" || lower == "tap center" || lower == "press center" -> tapCenter()
            lower.startsWith("tap ") -> {
                val coords = lower.removePrefix("tap ").split(Regex("[, ]+")).filter { it.isNotBlank() }
                if (coords.size == 2) {
                    val x = coords[0].toFloatOrNull()
                    val y = coords[1].toFloatOrNull()
                    if (x != null && y != null) tap(x, y) else null
                } else null
            }
            lower.startsWith("type ") || lower.startsWith("enter text ") -> {
                val value = if (lower.startsWith("type ")) q.substring(5) else q.substring(10)
                typeText(value)
            }
            else -> null
        }
    }

    private fun scroll(forward: Boolean): String {
        val root = rootInActiveWindow ?: return "No active app window is available for scrolling."
        val target = findScrollable(root) ?: root
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val success = target.performAction(action)
        target.recycle()
        root.recycle()
        return if (success) if (forward) "Scrolling down." else "Scrolling up." else "The current screen did not accept a scroll action."
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun typeText(value: String): String {
        val root = rootInActiveWindow ?: return "No active text field was found."
        val target = findEditable(root)
        if (target == null) {
            root.recycle()
            return "Tap a text field first, then ask JARVIS to type again."
        }
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        target.recycle()
        root.recycle()
        return if (success) "Entered the requested text." else "The current text field rejected text entry."
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun tapCenter(): String {
        val metrics = resources.displayMetrics
        return tap(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
    }

    private fun tap(x: Float, y: Float): String {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val accepted = dispatchGesture(gesture, null, null)
        return if (accepted) "Tap gesture sent at $x, $y." else "Android rejected the tap gesture."
    }

    companion object {
        @Volatile private var instance: JarvisAccessibilityService? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        fun handleCommand(command: String): String? {
            val service = instance ?: return when (command.trim().lowercase()) {
                "go back", "back", "press back", "navigate back",
                "go home", "home screen", "press home", "go to home screen",
                "show notifications", "open notifications", "notifications",
                "open quick settings", "quick settings", "scroll down", "scroll page down",
                "swipe up", "scroll up", "scroll page up", "swipe down",
                "tap", "tap center", "press center" -> "Enable JARVIS in Android Accessibility settings to use this action."
                else -> if (command.trim().lowercase().startsWith("tap ") ||
                    command.trim().lowercase().startsWith("type ") ||
                    command.trim().lowercase().startsWith("enter text ")) {
                    "Enable JARVIS in Android Accessibility settings to use this action."
                } else null
            }
            val lower = command.trim().lowercase()
            val supported = lower in setOf(
                "go back", "back", "press back", "navigate back",
                "go home", "home screen", "press home", "go to home screen",
                "show notifications", "open notifications", "notifications",
                "open quick settings", "quick settings", "scroll down", "scroll page down",
                "swipe up", "scroll up", "scroll page up", "swipe down",
                "tap", "tap center", "press center"
            ) || lower.startsWith("tap ") || lower.startsWith("type ") || lower.startsWith("enter text ")
            if (!supported) return null
            if (Looper.myLooper() == Looper.getMainLooper()) return service.execute(command)
            val task = FutureTask<String?> { service.execute(command) }
            mainHandler.post(task)
            return try {
                task.get(3, TimeUnit.SECONDS)
            } catch (_: Exception) {
                "Android did not complete that accessibility action in time."
            }
        }
    }
}
