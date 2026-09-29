import kotlin.math.pow
import kotlin.random.Random

class ZipfGenerator(
    seed: Int,
    private val cdfSize: Int = 1024
) {

    private val random = Random(seed)
    private val s = 1.15
    private val cdf = initCDF(cdfSize)

    /**
     * Инициализация массива накопленных вероятностей
     */
    private fun initCDF(cdfSize: Int): DoubleArray {
        val w = DoubleArray(cdfSize) { 1 / (it + 1).toDouble().pow(s) }
        val wSum = w.sum()
        var acc = 0.0;
        return DoubleArray(cdfSize) { i ->
            acc += w[i] / wSum
            acc
        }
    }

    /**
     * Генерирует набор данных с распределением Ципфа указанного размера
     */
    fun sample(size: Int) = LongArray(size) {
        val r = random.nextDouble()
        var index = cdf.binarySearch(r)
        if (index < 0) {
            index = -index - 1
        }
        (index + 1).coerceIn(1, cdfSize - 1).toLong()
    }
}