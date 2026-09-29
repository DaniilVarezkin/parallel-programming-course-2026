import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class InconsistencyTest {
    fun run(collector: MetricsCollector, sample: LongArray, countThreads: Int, countCalcSnap: Int = 10_000) {
        val startLatch = CountDownLatch(1)
        val stop = AtomicBoolean(false)

        val ops = LongArray(countThreads) { 0 }
        val threadArray = ArrayList<Thread>(countThreads)

        var countBroken = 0L
        var countBucketSumBigger = 0L
        var countTotalSumBigger = 0L

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

        startLatch.countDown()

        repeat(countCalcSnap) {
            val snap = collector.snapshot()
            val sumBucket = snap.buckets.sum()

            if (sumBucket != snap.count) {
                countBroken++

                if (sumBucket > snap.count) countBucketSumBigger++
                else countTotalSumBigger++
            }
        }

        stop.set(true)
        threadArray.forEach {
            it.join()
        }

        println("Сумма вызовов сделанных $countThreads потоками: ${ops.sum()}")
        println("Сумма вызовов в ${collector.javaClass.simpleName}: ${collector.snapshot().count}")
        println("Процент битых снимков: ${((countBroken.toDouble() / countCalcSnap) * 100.0).format(2)}%")
        println("Сколько раз сумма корзин оказалась меньше count: $countTotalSumBigger")
        println("Сколько раз сумма корзин оказалась больше count: $countBucketSumBigger")
        println("Разница: ${ops.sum() - collector.snapshot().count}")
    }
}