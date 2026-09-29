class SynchronizedEmptyRecordMetricsCollector : SingleThreadedMetricsCollector() {
    private val lock = Any()

    override fun record(value: Long) {
        synchronized(lock) {

        }
    }

    override fun snapshot(): Snapshot {
        synchronized(lock) {
            return super.snapshot()
        }
    }
}