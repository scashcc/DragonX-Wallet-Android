package cash.z.ecc.android.util

/**
 * A tiny, dependency-free record of the most recent UI navigation steps. It exists purely for crash
 * diagnosis: a hard-to-reproduce Compose runtime crash (ArrayIndexOutOfBoundsException in SlotTable
 * during a fragment's initial composition) does NOT name the offending screen in its stack trace, so
 * we stamp the last few fragment lifecycle steps here and the global crash handler appends them to
 * the crash log. That turns "some Compose screen crashed" into "ProfileFragment.onCreate crashed".
 */
object UiBreadcrumb {
    private const val MAX = 8
    private val trail = ArrayDeque<String>()

    @Synchronized
    fun note(step: String) {
        if (trail.size >= MAX) trail.removeFirst()
        trail.addLast(step)
    }

    /** Most recent step (the prime suspect for a crash happening right now). */
    @Synchronized
    fun last(): String = trail.lastOrNull() ?: "(none)"

    /** The whole recent trail, oldest -> newest, for context in the crash log. */
    @Synchronized
    fun trail(): String = if (trail.isEmpty()) "(none)" else trail.joinToString(" -> ")
}
