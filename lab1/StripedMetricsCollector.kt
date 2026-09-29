import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class StripedMetricsCollector(
    val shardCount: Int = 16
) : MetricsCollector {

    private class Shard(bucketsCount: Int) {
        val lock = Any()
        val buckets = LongArray(bucketsCount) { 0 }
    }

    /**
     * Гистограмма из 256 корзин. Каждая корзина содержит количество запросов, покрывает диапазон в 4 мс
     */
    private val shards = Array(shardCount) { Shard(256 / shardCount) }

    /**
     * Сколько всего вызовов [record] пришло
     */
    var count = AtomicLong(0L)
        private set

    /**
     * Сумма всех значений value (чтобы посчитать среднее: sum / count)
     */
    var sum = AtomicLong(0L)
        private set

    /**
     * Минимальное значение за всё время
     */
    var min = AtomicLong(Long.MAX_VALUE)
        private set

    /**
     * Максимальное значение за всё время
     */
    var max = AtomicLong(0L)
        private set

    override fun record(value: Long) {
        val bucketIndex = min(value / 4, 255).toInt()
        val shardIndex = bucketIndex % shardCount
        val indexInShard = bucketIndex / shardCount

        val shard = shards[shardIndex]

        synchronized(shard.lock) {
            shard.buckets[indexInShard]++
        }

        count.incrementAndGet()
        sum.addAndGet(value)

        var currentMin = min.get()
        while (value < currentMin) {
            if (min.compareAndSet(currentMin, value)) break
            currentMin = min.get()
        }

        var currentMax = max.get()
        while (value > currentMax) {
            if (max.compareAndSet(currentMax, value)) break
            currentMax = max.get()
        }
    }

    override fun snapshot(): Snapshot {
        val aggregatedBuckets = LongArray(256)

        for (shardIdx in 0 until shardCount) {
            val shard = shards[shardIdx]
            synchronized(shard.lock) {
                for (i in 0 until shard.buckets.size) {
                    val originalBucketIdx = i * shardCount + shardIdx
                    aggregatedBuckets[originalBucketIdx] = shard.buckets[i]
                }
            }
        }
        val totalCount = count.get()
        val currentSum = sum.get()
        val currentMin = min.get()
        val currentMax = max.get()

        return Snapshot(
            aggregatedBuckets,
            totalCount,
            currentSum,
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