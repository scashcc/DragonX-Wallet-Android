package cash.z.ecc.android.ext

import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.sdk.ext.ZcashSdk
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.tool.DerivationTool

/**
 * DragonX multi-wallet support.
 *
 * The app keeps ONE "active" wallet under the [Const.Backup] keys, and every fund-sensitive seed
 * reader (send / consolidate / export / backup / setup) already reads those keys. To support
 * multiple wallets WITHOUT touching any of those readers, each wallet's keys are mirrored into a
 * per-slot namespace. **Switching** simply copies the chosen slot's keys back into the active
 * [Const.Backup] keys and points the synchronizer at that wallet's own database (a distinct SDK
 * [alias]). Slot keys are never deleted, so a wallet can always be switched back to.
 *
 * The original (pre-multi-wallet) wallet becomes slot 0 with the default alias, so existing
 * installs are unaffected until the user explicitly creates or switches wallets.
 */
object WalletManager {
    private val box get() = DependenciesHolder.lockBox

    data class WalletMeta(
        val index: Int,
        val label: String,
        val alias: String,
        val isActive: Boolean
    )

    private const val NS = "cash.z.ecc.android.wallet"
    private fun k(i: Int, suffix: String) = "$NS.$i.$suffix"

    fun count(): Int = box.get<Int>(Const.Pref.WALLET_COUNT) ?: 0

    fun activeIndex(): Int = box.get<Int>(Const.Pref.WALLET_ACTIVE) ?: 0

    /** The SDK database alias of the active wallet (default alias for the original wallet). */
    fun activeAlias(): String =
        box.getCharsUtf8(k(activeIndex(), "alias"))?.let { String(it) } ?: ZcashSdk.DEFAULT_ALIAS

    /** Register the existing single wallet as slot 0 (default alias) if not yet registered. */
    fun migrateIfNeeded() {
        if (count() > 0) return
        // Register the pre-existing single wallet as slot 0. A wallet may be seed-based OR
        // private-key-based (imported via spending key, which is common for mining payout
        // addresses). The old guard only accepted HAS_SEED, so a private-key wallet was NEVER
        // registered: count() stayed 0, and the very next createWallet() ran with i=0, which
        // OVERWROTE slot 0's label (looked like the original wallet got "renamed"), flipped the
        // active alias to "wallet_0" (a fresh empty db -> full resync from scratch), and made
        // switchTo(0) early-return so the real new wallet only appeared on the SECOND tap. Accept
        // any completed wallet (seed, spending key, or at least a stored viewing key).
        val hasWallet = box.getBoolean(Const.Backup.HAS_SEED) ||
            box.getCharsUtf8(Const.Backup.SPENDING_KEY) != null ||
            box.getCharsUtf8(Const.Backup.VIEWING_KEY) != null
        if (!hasWallet) return
        saveActiveInto(0, label = "钱包 1 Wallet 1", alias = ZcashSdk.DEFAULT_ALIAS)
        box[Const.Pref.WALLET_COUNT] = 1
        box[Const.Pref.WALLET_ACTIVE] = 0
    }

    fun list(): List<WalletMeta> {
        val n = count()
        val active = activeIndex()
        return (0 until n).map { i ->
            val label = box.getCharsUtf8(k(i, "label"))?.let { String(it) } ?: "钱包 ${i + 1}"
            val alias = box.getCharsUtf8(k(i, "alias"))?.let { String(it) } ?: ZcashSdk.DEFAULT_ALIAS
            WalletMeta(i, label, alias, i == active)
        }
    }

    /** Copy the live [Const.Backup] keys into slot [i]'s namespace. */
    private fun saveActiveInto(i: Int, label: String, alias: String) {
        box.getBytes(Const.Backup.SEED)?.let { box.setBytes(k(i, "seed"), it) }
        box.getCharsUtf8(Const.Backup.SEED_PHRASE)?.let { box[k(i, "seed_phrase")] = it }
        // Private-key-restored wallets have no seed; their spending key lives here. Mirror it too so a
        // private-key wallet can be registered as a slot and switched back to.
        box.getCharsUtf8(Const.Backup.SPENDING_KEY)?.let { box[k(i, "spending_key")] = String(it) }
        box.getCharsUtf8(Const.Backup.VIEWING_KEY)?.let { box[k(i, "vk")] = String(it) }
        box.getCharsUtf8(Const.Backup.PUBLIC_KEY)?.let { box[k(i, "pub")] = String(it) }
        box.get<Int>(Const.Backup.BIRTHDAY_HEIGHT)?.let { box[k(i, "birthday")] = it }
        box[k(i, "label")] = label.toCharArray()
        box[k(i, "alias")] = alias.toCharArray()
    }

    /** Load slot [i]'s keys into the live [Const.Backup] keys (making it the active wallet). */
    private fun loadIntoActive(i: Int) {
        // CLEAR all live key material first. Critical for funds safety: a slot may be a seed wallet
        // OR a private-key wallet. Without clearing, switching from a seed wallet to a private-key
        // wallet (or vice-versa) would leave the PREVIOUS wallet's SEED/SPENDING_KEY behind, and
        // Keys.activeSpendingKey would then sign with the WRONG key. We set exactly the target slot's
        // fields below.
        box.delete(Const.Backup.SEED)
        box.delete(Const.Backup.SEED_PHRASE)
        box.delete(Const.Backup.SPENDING_KEY)
        box.delete(Const.Backup.VIEWING_KEY)
        box.delete(Const.Backup.PUBLIC_KEY)

        val seed = box.getBytes(k(i, "seed"))
        val seedPhrase = box.getCharsUtf8(k(i, "seed_phrase"))
        seed?.let { box.setBytes(Const.Backup.SEED, it) }
        seedPhrase?.let { box[Const.Backup.SEED_PHRASE] = it }
        box.getCharsUtf8(k(i, "spending_key"))?.let { box[Const.Backup.SPENDING_KEY] = String(it) }
        box.getCharsUtf8(k(i, "vk"))?.let { box[Const.Backup.VIEWING_KEY] = String(it) }
        box.getCharsUtf8(k(i, "pub"))?.let { box[Const.Backup.PUBLIC_KEY] = String(it) }
        box.get<Int>(k(i, "birthday"))?.let { box[Const.Backup.BIRTHDAY_HEIGHT] = it }
        // Derive the flags from what the slot actually has (robust for legacy slots saved before the
        // spending_key/flag fields existed: a seed present => seed wallet).
        box[Const.Backup.HAS_SEED] = (seed != null)
        box[Const.Backup.HAS_SEED_PHRASE] = (seedPhrase != null)
        box[Const.Backup.HAS_BACKUP] = true
    }

    /** Rename slot [i]. Only the display label changes; keys and the database are untouched. */
    fun rename(i: Int, newLabel: String) {
        val label = newLabel.trim().ifBlank { "钱包 ${i + 1}" }
        box[k(i, "label")] = label.toCharArray()
    }

    /** Switch the active wallet to slot [i] and rebuild the synchronizer for its database. */
    fun switchTo(i: Int) {
        if (i == activeIndex()) return
        loadIntoActive(i)
        box[Const.Pref.WALLET_ACTIVE] = i
        DependenciesHolder.resetSynchronizer()
    }

    /**
     * Create a brand-new wallet (new seed phrase, its own database via a fresh alias) and switch to
     * it. Returns its index. The previous wallet's keys remain safe in its own slot.
     */
    suspend fun createWallet(label: String): Int {
        // Make sure the pre-existing wallet occupies slot 0 BEFORE we pick the new index. Without
        // this, an unregistered original (count()==0) would make i=0 and clobber it (see
        // migrateIfNeeded). restoreFromSeed/restoreFromSpendingKey already do this; createWallet
        // must too.
        migrateIfNeeded()
        val network = ZcashWalletApp.instance.defaultNetwork
        val i = count()
        val mnemonics = DependenciesHolder.mnemonics
        val phrase = mnemonics.nextMnemonic(mnemonics.nextEntropy())
        val seed = mnemonics.toSeed(phrase)
        val vk = DerivationTool.deriveUnifiedViewingKeys(seed, network)[0]
        val birthday = BlockHeight.ofLatestCheckpoint(ZcashWalletApp.instance, network)
        val alias = "wallet_$i"

        box.setBytes(k(i, "seed"), seed)
        box[k(i, "seed_phrase")] = phrase
        box[k(i, "vk")] = vk.extfvk
        box[k(i, "pub")] = vk.extpub
        box[k(i, "birthday")] = birthday.value.toInt()
        box[k(i, "label")] = label.toCharArray()
        box[k(i, "alias")] = alias.toCharArray()
        box[Const.Pref.WALLET_COUNT] = i + 1

        switchTo(i)
        return i
    }

    /**
     * Restore an EXISTING wallet from a 24-word seed phrase as a NEW slot (its own database via a
     * fresh alias) and switch to it. The phrase must already be validated by the caller. Returns its
     * index. Existing wallets are untouched.
     */
    suspend fun restoreFromSeed(label: String, seedPhrase: String, birthday: BlockHeight): Int {
        migrateIfNeeded()
        val network = ZcashWalletApp.instance.defaultNetwork
        val i = count()
        val mnemonics = DependenciesHolder.mnemonics
        val phraseChars = seedPhrase.trim().toCharArray()
        val seed = mnemonics.toSeed(phraseChars)
        val vk = DerivationTool.deriveUnifiedViewingKeys(seed, network)[0]
        val alias = "wallet_$i"

        box.setBytes(k(i, "seed"), seed)
        box[k(i, "seed_phrase")] = phraseChars
        box[k(i, "vk")] = vk.extfvk
        box[k(i, "pub")] = vk.extpub
        box[k(i, "birthday")] = birthday.value.toInt()
        box[k(i, "label")] = label.toCharArray()
        box[k(i, "alias")] = alias.toCharArray()
        box[Const.Pref.WALLET_COUNT] = i + 1

        switchTo(i)
        return i
    }

    /**
     * Restore an EXISTING wallet from a Sapling spending key (secret-extended-key-main…) as a NEW
     * slot and switch to it. The viewing key is derived from (and thereby validates) the spending
     * key; a bad key throws. There is no seed/transparent component, so a placeholder extpub is
     * stored (see [Keys]). Returns its index. Existing wallets are untouched.
     */
    suspend fun restoreFromSpendingKey(label: String, spendingKey: String, birthday: BlockHeight): Int {
        migrateIfNeeded()
        val network = ZcashWalletApp.instance.defaultNetwork
        val key = spendingKey.trim()
        val i = count()
        val extfvk = DerivationTool.deriveViewingKey(key, network) // validates the key; throws if bad
        val alias = "wallet_$i"

        box[k(i, "spending_key")] = key.toCharArray()
        box[k(i, "vk")] = extfvk
        box[k(i, "pub")] = Keys.PLACEHOLDER_EXTPUB
        box[k(i, "birthday")] = birthday.value.toInt()
        box[k(i, "label")] = label.toCharArray()
        box[k(i, "alias")] = alias.toCharArray()
        box[Const.Pref.WALLET_COUNT] = i + 1

        switchTo(i)
        return i
    }
}
