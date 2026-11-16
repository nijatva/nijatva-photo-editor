package com.nijatva.photo.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nijatva.photo.editor.edit.EditState

/** Ordered edit stack with full undo / redo. */
class EditHistory {
    var state by mutableStateOf(EditState())
        private set

    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Live update while a slider is being dragged; not recorded yet. */
    fun update(next: EditState) {
        state = next
    }

    /** Record a completed change (call when the gesture / tap ends). */
    fun commit(previous: EditState) {
        if (previous != state) {
            undoStack.addLast(previous)
            redoStack.clear()
        }
    }

    fun apply(next: EditState) {
        commit(state)
        state = next
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(state)
        state = prev
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(state)
        state = next
    }

    fun reset() {
        apply(EditState())
    }
}
