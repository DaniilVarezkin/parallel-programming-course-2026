import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    val sample = ZipfGenerator(13).sample(1 shl 20)
    println("Данные подготовлены")

    val threadsCount = arrayOf(1, 2, 4, 8, 12)

    threadsCount.forEach { t ->
        runTest({ThreadBinaryBuffersMetricsCollector()}, sample, t)
    }

    val test = InconsistencyTest()
    test.run(ThreadBinaryBuffersMetricsCollector(), sample, 4)
}

fun runTest(
    collectorFactory: () -> MetricsCollector,
    sample: LongArray,
    countThreads: Int
) {
    println("==========================================")
    println("$countThreads потоков:")
    val median = measurePoint(
        collectorFactory,
        sample,
        5000L,
        countThreads,
    )

    println("$countThreads потоков: ${(median / 1e6).format(2)} млн ops/sec")
    println("==========================================\n")
}

/**
 * Запускает [countThreads] потоков в течении [durationMs] секунд.
 * @return Число операций в секунду
 */
fun run(
    collector: MetricsCollector,
    sample: LongArray,
    durationMs: Long,
    countThreads: Int = 1
): Double {
    val startLatch = CountDownLatch(1)
    val stop = AtomicBoolean(false)

    val ops = LongArray(countThreads) { 0 }
    val threadArray = ArrayList<Thread>(countThreads)

    repeat(countThreads) { k ->
        val thread = Thread {
            var localCounter = 0L
            var i = (1000 * k) % sample.size

            startLatch.await()

            while (!stop.get()) {
                collector.record(sample[i])
                localCounter++
                i++
                if (i == sample.size) i = 0
            }

            ops[k] = localCounter
        }

        threadArray.add(thread)
        thread.start()
    }

    val t0 = System.nanoTime()

    startLatch.countDown()
    sleep(durationMs)
    stop.set(true)

    val t1 = System.nanoTime()

    threadArray.forEach {
        it.join()
    }

    val elapsedTimeSeconds = (t1 - t0) / 1e9
    val totalOps = ops.sum()

    return totalOps / elapsedTimeSeconds
}

/**
 * Прогревает, затем считает медиану операций в секунду для заданного набора данных и количества процессов
 */
fun measurePoint(
    collectorFactory: () -> MetricsCollector,
    sample: LongArray,
    durationMs: Long,
    countThreads: Int,
    countMeasurers: Int = 5
): Double {
    println("Прогрев...")
    run(collectorFactory(), sample, durationMs, countThreads)

    println("Прогрев завершён. Начало замеров:")
    println("Для ${collectorFactory().javaClass.simpleName} в $countThreads потоков.")

    val results = mutableListOf<Double>();
    repeat(countMeasurers) {
        val collector = collectorFactory()

        val measurement = run(collector, sample, durationMs, countThreads)
        results.add(measurement)

        // println("Замер №${it + 1}: ${(measurement / 1e6).format(2)} млн ops/sec. \tSnapshot count: ${collector.snapshot().count}")
    }
    return median(results)
}

/**
 * Вспомогательная функция которая возвращает медиану в списке чисел
 */
fun median(results: List<Double>): Double {
    return results.sorted()[results.size / 2]
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)