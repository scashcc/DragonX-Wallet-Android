package cash.z.ecc.android.sdk.ext

import cash.z.ecc.android.sdk.model.Zatoshi

/**
 * Wrapper for all the constant values in the SDK. It is important that these values stay fixed for
 * all users of the SDK. Otherwise, if individual wallet makers are using different values, it
 * becomes easier to reduce privacy by segmenting the anonymity set of users, particularly as it
 * relates to network requests.
 */
object ZcashSdk {

    /**
     * Miner's fee in zatoshi. DragonX requires shielded transactions to pay at least
     * 10000 zatoshi (0.0001 DRGX) — this matches the node's
     * ASYNC_RPC_OPERATION_DEFAULT_MINERS_FEE and the Rust DEFAULT_FEE. Keep these in sync.
     */
    val MINERS_FEE = Zatoshi(10_000L)

    /**
     * Maximum number of input notes to sweep per consolidation round. Must stay below the node's
     * 50-input "large transaction" threshold (LARGE_ZINS_THRESHOLD in dragonx/src/miner.cpp) so
     * each consolidation tx is a "normal" tx that miners include immediately. 45 leaves a margin.
     */
    const val MAX_CONSOLIDATION_INPUTS = 45

    /**
     * The theoretical maximum number of blocks in a reorg, due to other bottlenecks in the protocol design.
     */
    val MAX_REORG_SIZE = 100

    /**
     * The maximum length of a memo.
     */
    val MAX_MEMO_SIZE = 512

    /**
     * The amount of blocks ahead of the current height where new transactions are set to expire. This value is controlled
     * by the rust backend but it is helpful to know what it is set to and should be kept in sync.
     */
    val EXPIRY_OFFSET = 20

    /**
     * Default size of batches of blocks to request from the compact block service.
     */
    // Because blocks are buffered in memory upon download and storage into SQLite, there is an upper bound
    // above which OutOfMemoryError is thrown. DragonX compact blocks are small (~1-2kb each),
    // so a larger batch size significantly reduces gRPC call overhead during initial sync.
    const val DOWNLOAD_BATCH_SIZE = 500

    /**
     * Default size of batches of blocks to scan via librustzcash. The smaller this number the more granular information
     * can be provided about scan state. Unfortunately, it may also lead to a lot of overhead during scanning.
     * DragonX blocks are small, so larger batches reduce JNI/FFI overhead significantly.
     */
    val SCAN_BATCH_SIZE = 2500

    /**
     * Default amount of time, in milliseconds, to poll for new blocks. Typically, this should be about half the average
     * block time.
     */
    val POLL_INTERVAL = 18_000L

    /**
     * Estimate of the time between blocks.
     */
    val BLOCK_INTERVAL_MILLIS = 36_000L

    /**
     * Default attempts at retrying.
     */
    val RETRIES = 5

    /**
     * The default maximum amount of time to wait during retry backoff intervals. Failed loops will never wait longer than
     * this before retyring.
     */
    val MAX_BACKOFF_INTERVAL = 600_000L

    /**
     * Default number of blocks to rewind when a chain reorg is detected. This should be large enough to recover from the
     * reorg but smaller than the theoretical max reorg size of 100.
     */
    val REWIND_DISTANCE = 10

    val DB_DATA_NAME = "Data.db"
    val DB_CACHE_NAME = "Cache.db"

    /**
     * File name for the sappling spend params
     */
    val SPEND_PARAM_FILE_NAME = "sapling-spend.params"

    /**
     * File name for the sapling output params
     */
    val OUTPUT_PARAM_FILE_NAME = "sapling-output.params"

    /**
     * The Url that is used by default in hushd.
     * We'll want to make this externally configurable, rather than baking it into the SDK but
     * this will do for now
     */
    val CLOUD_PARAM_DIR_URL = listOf("https://storage.hush.land/hush3/",
        "https://git.hush.is/hush/hush3/raw/branch/master/",
        "https://github.com/hushmirror/hush3/raw/dev/")

    /**
     * The default memo to use when shielding transparent funds.
     */
    val DEFAULT_SHIELD_FUNDS_MEMO_PREFIX = "shielding:"

    val DEFAULT_ALIAS: String = "ZcashSdk"
}
