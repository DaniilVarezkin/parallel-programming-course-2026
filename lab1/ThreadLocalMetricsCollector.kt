import kotlin.collections.ArrayList
import kotlin.math.min

open class ThreadLocalMetricsCollector : MetricsCollector {

    private val allStates: MutableList<ThreadState> = mutableListOf()

    private val myState = ThreadLocal.withInitial {
        val state = ThreadState()
        allStates.add(state)
        state
    }

    override fun record(value: Long) {
        val s = myState.get()
        val bucket = min(value / 4, 255).toInt()

        s.buckets.setRelease(bucket, s.buckets.getPlain(bucket) + 1)
        s.count.setRelease(s.count.getPlain() + 1)
        s.sum.setRelease(s.sum.getPlain() + value)

        if (value < s.min.getPlain()) {
            s.min.setRelease(value)
        }
        if (value > s.max.getPlain()) {
            s.max.setRelease(value)
        }
    }

    override fun snapshot(): Snapshot {
        val aggregatedBuckets = LongArray(256)
        var totalCount = 0L
        var totalSum = 0L
        var globalMin = Long.MAX_VALUE
        var globalMax = 0L

        val statesCopy = synchronized(allStates) { ArrayList(allStates) }

        for (s in statesCopy) {
            for (i in 0 until 256) {
                aggregatedBuckets[i] += s.buckets.get(i)
            }
            val c = s.count.get()
            totalCount += c
            totalSum += s.sum.get()

            if (c > 0) {
                globalMin = minOf(globalMin, s.min.get())
                globalMax = maxOf(globalMax, s.max.get())
            }
        }

        val currentMin = if (totalCount == 0L) 0L else globalMin
        val currentMax = globalMax

        return Snapshot(
            aggregatedBuckets,
            totalCount,
            totalSum,
            currentMin,
            currentMax,
            calcP(0.5, aggregatedBuckets, totalCount).toLong(),
            calcP(0.99, aggregatedBuckets, totalCount).toLong()
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