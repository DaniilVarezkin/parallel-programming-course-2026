class SynchronizedMetricsCollector : SingleThreadedMetricsCollector() {
    private val lock = Any()

    override fun record(value: Long) {
        synchronized(lock) {
            super.record(value)
        }
    }

    override fun snapshot(): Snapshot {
        synchronized(lock) {
            return super.snapshot()
        }
    }
}