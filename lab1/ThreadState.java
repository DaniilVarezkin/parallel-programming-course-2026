import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

final class ThreadState {
    final AtomicLongArray buckets = new AtomicLongArray(256);
    final AtomicLong count = new AtomicLong();
    final AtomicLong sum = new AtomicLong();
    final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong max = new AtomicLong(0);
}