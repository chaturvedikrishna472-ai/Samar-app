package com.samar.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SamarAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SamarAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event-driven updates can be wired here later if needed.
    }

    override fun onInterrupt() {
        // Keep service instance alive; system will recover or rebind.
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance == this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    fun performTap(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f) return false
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun clickNodeByText(targetText: String): Boolean {
        val clean = targetText.trim()
        if (clean.isEmpty()) return false
        val root = rootInActiveWindow ?: return false
        val match = findMatchingNode(root, clean) ?: return false
        val clickableNode = findClickableAncestor(match) ?: match
        return clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val targetNode = findEditableNode(root) ?: return false
        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun performScroll(down: Boolean = true): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root) ?: return false
        val action = if (down) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return scrollable.performAction(action)
    }

    fun readScreenText(maxItems: Int = 25): String {
        val root = rootInActiveWindow ?: return "Screen ka data abhi read nahi ho paaya."
        val items = mutableListOf<String>()
        collectNodeTexts(root, items, maxItems.coerceAtLeast(1))
        return if (items.isEmpty()) {
            "Screen par koi clear text nahi mila."
        } else {
            items.joinToString(" | ")
        }
    }

    fun performSystemBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun performSystemHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun performSystemRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    private fun findMatchingNode(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.trim()
        val nodeDesc = node.contentDescription?.toString()?.trim()
        if (
            (!nodeText.isNullOrEmpty() && nodeText.equals(query, ignoreCase = true)) ||
            (!nodeDesc.isNullOrEmpty() && nodeDesc.equals(query, ignoreCase = true))
        ) {
            return node
        }
        if (
            (!nodeText.isNullOrEmpty() && nodeText.contains(query, ignoreCase = true)) ||
            (!nodeDesc.isNullOrEmpty() && nodeDesc.contains(query, ignoreCase = true))
        ) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findMatchingNode(child, query)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun collectNodeTexts(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        limit: Int
    ) {
        if (out.size >= limit) return
        val label = (node.text?.toString()?.trim()).takeUnless { it.isNullOrEmpty() }
            ?: (node.contentDescription?.toString()?.trim()).takeUnless { it.isNullOrEmpty() }
        if (label != null && !out.contains(label)) {
            out.add(label)
        }
        for (i in 0 until node.childCount) {
            if (out.size >= limit) break
            val child = node.getChild(i) ?: continue
            collectNodeTexts(child, out, limit)
        }
    }
}
