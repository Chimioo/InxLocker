package io.github.chimio.inxlocker.util

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService

object HotReloadTrigger {

    @Volatile
    private var isRunning = false

    data class Outcome(
        val total: Int,
        val success: Int,
        val failed: Int,
        val skipped: Int,
        val processDied: Int,
        val perTarget: List<Line>
    ) {
        data class Line(
            val processName: String,
            val pid: Int,
            val status: HotReloadResult.Status,
            val message: String?
        )
    }

    sealed class Capability {
        object Available : Capability()
        object NoService : Capability()
        object Unsupported : Capability()
    }

    fun probe(): Capability {
        val service = XposedServiceHolder.get() ?: return Capability.NoService
        val ok = runCatching {
            service.apiVersion >= XposedService.API_102
        }.getOrDefault(false)
        return if (ok) Capability.Available else Capability.Unsupported
    }

    fun reloadAllStale(
        onlyStale: Boolean = true,
        onProgress: ((Outcome.Line) -> Unit)? = null,
        onFinished: (Outcome) -> Unit
    ): Boolean {
        if (isRunning) return false
        isRunning = true

        val main = Handler(Looper.getMainLooper())
        val service = XposedServiceHolder.get()
        if (service == null) {
            main.post {
                isRunning = false
                onFinished(Outcome(0, 0, 0, 0, 0, emptyList()))
            }
            return true
        }

        val targets = runCatching { service.runningTargets }.getOrNull().orEmpty()
        val candidates = if (onlyStale) {
            targets.filter { it.state == HookedTarget.State.STALE }
        } else {
            targets.filter {
                it.state == HookedTarget.State.STALE ||
                    it.state == HookedTarget.State.UP_TO_DATE
            }
        }

        if (candidates.isEmpty()) {
            main.post {
                isRunning = false
                onFinished(Outcome(0, 0, 0, 0, 0, emptyList()))
            }
            return true
        }

        val total = candidates.size
        val results = mutableListOf<Outcome.Line>()
        val pending = java.util.concurrent.atomic.AtomicInteger(total)

        val done = {
            isRunning = false
            onFinished(summarize(total, results))
        }

        candidates.forEach { proc ->
            runCatching {
                service.hotReloadModule(proc, null) { p, result ->
                    val line = Outcome.Line(
                        processName = p.processName,
                        pid = p.pid,
                        status = result.status,
                        message = result.message
                    )
                    main.post {
                        synchronized(results) { results += line }
                        onProgress?.invoke(line)
                        if (pending.decrementAndGet() == 0) done()
                    }
                }
            }.onFailure { t ->
                val line = Outcome.Line(
                    processName = proc.processName,
                    pid = proc.pid,
                    status = HotReloadResult.Status.FAILED,
                    message = t.message ?: t.javaClass.simpleName
                )
                main.post {
                    synchronized(results) { results += line }
                    onProgress?.invoke(line)
                    if (pending.decrementAndGet() == 0) done()
                }
            }
        }
        return true
    }

    private fun summarize(total: Int, lines: List<Outcome.Line>): Outcome {
        var success = 0
        var failed = 0
        var skipped = 0
        var died = 0
        lines.forEach {
            when (it.status) {
                HotReloadResult.Status.SUCCEEDED -> success++
                HotReloadResult.Status.FAILED -> failed++
                HotReloadResult.Status.UNSUPPORTED -> skipped++
                HotReloadResult.Status.IN_PROGRESS -> skipped++
                HotReloadResult.Status.PROCESS_DIED -> died++
            }
        }
        return Outcome(total, success, failed, skipped, died, lines)
    }
}
