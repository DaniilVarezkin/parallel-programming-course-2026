public record Snapshot(
        long[] buckets, // ровно 256 элементов (глубокая копия, не ссылка!)
        long count,
        long sum,
        long min,
        long max,
        long p50,       // в мс: индекс_корзины * 4
        long p99
) {}