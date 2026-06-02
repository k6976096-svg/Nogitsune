package io.kitsuri.nogitsune.ui.components

import java.util.LinkedList

class LimitedQueue<E>(private val limit: Int) : LinkedList<E>() {
    override fun add(element: E): Boolean {
        super.add(element)
        while (size > limit) remove()
        return true
    }
}
