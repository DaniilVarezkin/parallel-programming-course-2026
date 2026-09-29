import java.util.concurrent.atomic.AtomicInteger;

final class ThreadBuffers {
    // Два буфера: [0] и [1]. Обычные массивы и переменные!
    final long[][] buckets = new long[2][256];
    final long[] count = new long[2];
    final long[] sum = new long[2];
    final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
    final long[] max = {0, 0};

    // Флаг входа: -1 = вне буферов, 0 = запись в буфер 0, 1 = запись в буфер 1
    final AtomicInteger inside = new AtomicInteger(-1);
}