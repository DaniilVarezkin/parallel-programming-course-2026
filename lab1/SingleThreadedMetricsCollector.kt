import kotlin.math.min

open class SingleThreadedMetricsCollector : MetricsCollector {
    /**
     * Гистограмма из 256 корзин. Каждая корзина содержит количество запросов, покрывает диапазон в 4 мс
     */
    private val buckets = LongArray(256) { 0 }

    /**
     * Сколько всего вызовов [record] пришло
     */
    var count: Long = 0
        private set

    /**
     * Сумма всех значений value (чтобы посчитать среднее: sum / count)
     */
    var sum: Long = 0
        private set

    /**
     * Минимальное значение за всё время
     */
    var min: Long = Long.MAX_VALUE
        private set

    /**
     * Максимальное значение за всё время
     */
    var max: Long = 0
        private set

    override fun record(value: Long) {
        buckets[min(value / 4, 255).toInt()]++

        count++
        sum += value

        min = minOf(min, value)
        max = maxOf(max, value)
    }

    override fun snapshot(): Snapshot {
        return Snapshot(
            buckets.clone(),
            count,
            sum,
            min ?: 0,
            max ?: 0,
            calcP(0.5).toLong(),
            calcP(0.99).toLong()
        )
    }

    private fun calcP(p: Double): Int {
        if (p < 0.0 || p > 1.0) {
            throw IllegalArgumentException("Некорректное значение перцентиля")
        }

        val threshold = p * count
        var acc = 0L

        for (i in buckets.indices) {
            acc += buckets[i]
            if (acc >= threshold) return i * 4
        }
        return buckets.lastIndex * 4
    }

}