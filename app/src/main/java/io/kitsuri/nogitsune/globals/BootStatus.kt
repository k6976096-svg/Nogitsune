package io.kitsuri.nogitsune.globals

import java.util.concurrent.BrokenBarrierException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

object BootStatus {
    private val booted = AtomicBoolean(false)
    private val latch = CyclicBarrier(2)

    fun markBooted() {
        if (booted.compareAndSet(false, true)) {
            runCatching { latch.await() }
        }
    }

    fun isBooted() = booted.get()

    fun waitBoot(timeout: Long, unit: TimeUnit): Boolean {
        return try {
            latch.await(timeout, unit)
            true
        } catch (_: TimeoutException) {
            false
        } catch (_: BrokenBarrierException) {
            false
        } catch (_: InterruptedException) {
            false
        }
    }

    fun reset() {
        booted.set(false)
        latch.reset()
    }
}
