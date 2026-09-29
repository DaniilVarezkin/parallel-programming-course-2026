import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

open class ThreadBinaryBuffersMetricsCollector : MetricsCollector {
    @Volatile
    private var active = 0

    // Глобальная аккумуляция (накопленный итог за всё время)
    private val globalBuckets = LongArray(256)
    private var globalCount = 0L
    private var globalSum = 0L
    private var globalMin = Long.MAX_VALUE
    private var globalMax = 0L

    // Блокировка для snapshot() и регистрации новых потоков
    private val snapLock = Any()
    private val allStates = ArrayList<ThreadBuffers>()

    private val myState = ThreadLocal.withInitial {
        val state = ThreadBuffers()
        synchronized(snapLock) {
            allStates.add(state)
        }
        state
    }

    override fun record(value: Long) {
        val s = myState.get()
        val bucket = min(value / 4, 255).toInt()

        var b: Int
        // Рукопожатие Деккера
        while (true) {
            b = active
            s.inside.set(b)
            if (active == b) {
                break
            }
            s.inside.setRelease(-1)
        }

        s.buckets[b][bucket]++
        s.count[b]++
        s.sum[b] += value

        if (value < s.min[b]) s.min[b] = value
        if (value > s.max[b]) s.max[b] = value

        s.inside.setRelease(-1)
    }

    override fun snapshot(): Snapshot = synchronized(snapLock) {
        val old = active
        active = 1 - old

        val statesCopy = ArrayList(allStates)

        for (s in statesCopy) {
            while (s.inside.get() == old) {
                Thread.onSpinWait()
            }
        }
        for (s in statesCopy) {
            val c = s.count[old]
            if (c > 0) {
                globalCount += c
                globalSum += s.sum[old]
                globalMin = minOf(globalMin, s.min[old])
                globalMax = maxOf(globalMax, s.max[old])

                for (i in 0 until 256) {
                    globalBuckets[i] += s.buckets[old][i]
                }

                // Очистка замороженного буфера
                s.buckets[old].fill(0L)
                s.count[old] = 0L
                s.sum[old] = 0L
                s.min[old] = Long.MAX_VALUE
                s.max[old] = 0L
            }
        }

        val currentMin = if (globalCount == 0L) 0L else globalMin
        val currentMax = globalMax
        val bucketsCopy = globalBuckets.clone()

        return Snapshot(
            bucketsCopy,
            globalCount,
            globalSum,
            currentMin,
            currentMax,
            calcP(0.5, bucketsCopy, globalCount).toLong(),
            calcP(0.99, bucketsCopy, globalCount).toLong()
        )
    }

    private fun calcP(p: Double, buckets: LongArray, totalCount: Long): Int {
        if (p < 0.0 || p > 1.0) {
            throw IllegalArgumentException("Некорректное значение перцентиля")
        }

        val threshold = p * totalCount
        var acc = 0L

        for (i in buckets.indices) {
            acc += buckets[i]
            if (acc >= threshold) return i * 4
        }
        return buckets.lastIndex * 4
    }
}