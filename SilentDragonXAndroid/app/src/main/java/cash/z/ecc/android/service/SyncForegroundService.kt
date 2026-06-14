package cash.z.ecc.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample

/**
 * Foreground service that keeps the wallet syncing while the app is in the background.
 *
 * Why: the sync engine is a process-singleton, but Android (especially aggressive OEMs like MIUI)
 * kills backgrounded app processes to reclaim memory. A process kill mid-write is exactly what
 * corrupts the block database (the "database disk image is malformed" loop). A foreground service
 * with an ongoing notification tells the system "this app is doing user-visible work, don't kill
 * it", so the sync can keep running long-term in the background and the DB stops getting corrupted.
 *
 * It also holds a partial wake lock so the CPU keeps scanning while the screen is off. The actual
 * sync work lives in the Synchronizer; this service just keeps the process alive and mirrors the
 * sync status into the notification.
 */
class SyncForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        twig("SyncForegroundService.onCreate")
        createChannel()
        startForeground(NOTIF_ID, buildNotification("正在准备后台同步…"))
        acquireWakeLock()
        observeSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            twig("SyncForegroundService stop requested")
            BackgroundSyncManager.setEnabledInternal(this, false)
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        // If the process was killed and restarted, re-bind the observer.
        if (observeJob == null) observeSync()
        // START_STICKY: ask the system to recreate us (and thus re-pin the process) if it kills us.
        return START_STICKY
    }

    private fun observeSync() {
        observeJob?.cancel()
        val sync = runCatching { DependenciesHolder.synchronizer }.getOrNull() ?: return
        observeJob = combine(sync.status, sync.progress) { status, progress -> status to progress }
            .sample(1500) // avoid hammering the notification on every batch
            .onEach { (status, progress) -> updateNotification(statusText(status, progress)) }
            .launchIn(scope)
    }

    private fun statusText(status: Synchronizer.Status, progress: Int): String = when (status) {
        Synchronizer.Status.SYNCED -> "已同步 · 钱包余额已最新"
        Synchronizer.Status.DOWNLOADING -> "后台同步中：下载区块…"
        Synchronizer.Status.VALIDATING -> "后台同步中：校验区块…"
        Synchronizer.Status.SCANNING -> "后台同步中：扫描 $progress%"
        Synchronizer.Status.ENHANCING -> "后台同步中：整理交易 $progress%"
        Synchronizer.Status.PREPARING -> "正在准备同步…"
        Synchronizer.Status.DISCONNECTED -> "正在连接节点…"
        Synchronizer.Status.STOPPED -> "同步已停止"
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DragonX:BackgroundSync").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { twig("SyncForegroundService: wake lock failed: $it") }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台同步 Background sync",
                NotificationManager.IMPORTANCE_LOW // no sound/vibration; quiet ongoing status
            ).apply {
                description = "保持钱包在后台同步的常驻通知"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SyncForegroundService::class.java).setAction(ACTION_STOP),
            pendingFlags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("DragonX 钱包")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, "停止后台同步", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID, buildNotification(text))
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        twig("SyncForegroundService.onDestroy")
        observeJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "dragonx_sync"
        private const val NOTIF_ID = 4711
        const val ACTION_STOP = "cash.z.ecc.android.service.STOP_BACKGROUND_SYNC"
    }
}
