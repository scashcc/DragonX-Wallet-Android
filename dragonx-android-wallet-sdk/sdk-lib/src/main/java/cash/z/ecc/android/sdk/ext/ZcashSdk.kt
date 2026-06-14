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
     * Maximum number of input notes merged per consolidation transaction. DragonX lowered this from
     * 45 to 8: a smaller batch keeps every consolidation tx tiny (~3-4 KB) so miners reliably
     * include it, matching the node's own zsweep default (zsweepmaxinputs=8). It must stay well
     * below the node's 50-input "large transaction" threshold (LARGE_ZINS_THRESHOLD in
     * dragonx/src/miner.cpp). More, smaller batches just means more rounds — the consolidation
     * sweep paces and repeats them automatically.
     */
    const val MAX_CONSOLIDATION_INPUTS = 8

    /**
     * Maximum number of consolidation transactions kept in flight (submitted but not yet confirmed)
     * at once. The sweep never fire-and-forgets the whole wallet into the mempool: it keeps at most
     * this many batches pending, waits for each to actually confirm on-chain (>=1 confirmation),
     * then tops the queue back up. This paces submission to match the chain (mostly empty blocks
     * with the occasional block that packs several txs) and avoids piling up unconfirmed txs that
     * could double-spend each other or expire. Set to 1 for fully serial (most conservative).
     */
    const val MAX_CONSOLIDATION_INFLIGHT = 6

    /**
     * How many idle poll cycles to wait — with nothing in flight and nothing new to encode — before
     * the consolidation sweep concludes it is finished. This bridges the gap while freshly-merged
     * output notes mature into spendable notes, so the sweep keeps going across passes
     * (e.g. 722 notes -> ~90 -> ~12 -> 1) in a single run instead of stopping after the first pass.
     */
    const val CONSOLIDATION_IDLE_GRACE_ROUNDS = 3

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
     *
     * DragonX: lowered 2500 -> 100. For a mining wallet with many small notes, witness updates make
     * each scanned block expensive; one 2500-block batch can take many minutes, during which the
     * "Scanning X%" UI is frozen (the % only advances when a whole batch finishes) — users read the
     * long freeze as a hang ("卡在 90% 不动"). 100 keeps each batch short so the % moves visibly and
     * every batch commits its scan progress (a backgrounded/killed app then loses far less work).
     * Total scan work is essentially unchanged; this trades a little FFI overhead for live progress.
     */
    val SCAN_BATCH_SIZE = 100

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
