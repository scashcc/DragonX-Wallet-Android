package cash.z.ecc.android.sdk.model

/**
 * A unit of currency used throughout the SDK.
 *
 * End users (e.g. app users) generally are not shown Zatoshi values.  Instead they are presented
 * with ZEC, which is a decimal value represented only as a String.  ZEC are not used internally,
 * to avoid floating point imprecision.
 */
data class Zatoshi(val value: Long) : Comparable<Zatoshi> {
    operator fun plus(other: Zatoshi) = Zatoshi(value + other.value)
    operator fun minus(other: Zatoshi) = Zatoshi(value - other.value)

    override fun compareTo(other: Zatoshi) = value.compareTo(other.value)

    companion object {
        /**
         * The number of Zatoshi that equal 1 ZEC.
         */
        const val ZATOSHI_PER_ZEC = 100_000_000L
    }
}
