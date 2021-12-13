@file:Suppress("unused")

package com.dfsek.terra.biometool.util

import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import kotlin.concurrent.thread


fun onJvmShutdown(block: () -> Unit): Thread = onJvmShutdown("JVM-Shutdown-Thread", block)


fun onJvmShutdown(name: String, block: () -> Unit): Thread = thread(start = false, isDaemon = false, name = name, block = block).also {
    Runtime.getRuntime().addShutdownHook(it)
}

fun removeJvmShutdownThread(thread: Thread) = Runtime.getRuntime().removeShutdownHook(thread)

val runtime: Runtime
    get() = Runtime.getRuntime()

/**
 * [Runtime.freeMemory]
 */
val Runtime.freeMemory: Long
    get() = freeMemory()

/**
 * [Runtime.totalMemory]
 */
val Runtime.totalMemory: Long
    get() = totalMemory()

/**
 * [Runtime.maxMemory]
 */
val Runtime.maxMemory: Long
    get() = maxMemory()

val Runtime.processors: Int
    get() = availableProcessors()

val Runtime.availableProcessors: Int
    get() = availableProcessors()

val runtimeMXBean: RuntimeMXBean
    get() = ManagementFactory.getRuntimeMXBean()