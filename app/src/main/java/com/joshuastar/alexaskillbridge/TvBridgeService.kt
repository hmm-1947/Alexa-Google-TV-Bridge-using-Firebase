package com.joshuastar.alexaskillbridge

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TvBridgeService : Service() {

    private var lastTimestamp: Long = 0

    companion object {
        private const val CHANNEL_ID = "tv_bridge_channel"
        private const val LAUNCH_CHANNEL_ID = "tv_bridge_launch_channel"
        private const val NOTIF_ID_SERVICE = 1
        private const val NOTIF_ID_LAUNCH = 2

        private val TV_PACKAGE_MAP = mapOf(
            "smarttube"      to "com.teamsmart.videomanager.tv",
            "smart tube"     to "com.teamsmart.videomanager.tv",
            "youtube"        to "com.google.android.youtube.tv",
            "youtube music"  to "com.google.android.youtube.tvmusic",
            "netflix"        to "com.netflix.ninja",
            "prime video"    to "com.amazon.amazonvideo.livingroom",
            "prime"          to "com.amazon.amazonvideo.livingroom",
            "hotstar"        to "in.startv.hotstar",
            "disney"         to "in.startv.hotstar",
            "disney hotstar" to "in.startv.hotstar",
            "jio hotstar"    to "in.startv.hotstar",
            "spotify"        to "com.spotify.tv.android",
            "vlc"            to "org.videolan.vlc",
            "stremio"        to "com.stremio.one",
            "brave"          to "com.brave.browser",
            "browser"        to "com.brave.browser",
            "localsend"      to "org.localsend.localsend_app",
            "local send"     to "org.localsend.localsend_app",
            "mx player"      to "com.mxtech.videoplayer.television",
            "anydesk"        to "com.anydesk.anydeskandroid",
            "play store"     to "com.android.vending",
            "playstore"      to "com.android.vending",
            "manorama"       to "com.mmtv.manoramamax.android",
            "settings"       to "com.android.tv.settings"
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIF_ID_SERVICE, buildServiceNotification())
        lastTimestamp = System.currentTimeMillis()
        listenForCommands()
    }

    // ── Notifications ──────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TV Bridge Service", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(LAUNCH_CHANNEL_ID, "App Launch", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Used to launch apps via Alexa" }
        )
    }

    private fun buildServiceNotification() =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Alexa TV Bridge")
            .setContentText("Listening for commands...")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .build()

    private fun showLaunchNotification(title: String, text: String, intent: Intent) {
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, LAUNCH_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID_LAUNCH, notif)
    }

    // ── Firebase listener ──────────────────────────────────────────────────────

    private fun listenForCommands() {
        FirebaseDatabase.getInstance()
            .getReference("tv_commands/current")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0
                    if (timestamp <= lastTimestamp) return
                    lastTimestamp = timestamp

                    val action = snapshot.child("action").getValue(String::class.java)
                    val app    = snapshot.child("app").getValue(String::class.java)
                    val query  = snapshot.child("query").getValue(String::class.java) ?: ""

                    when (action) {
                        "open"      -> app?.let { openApp(it) }
                        "search"    -> app?.let { searchInApp(it, query) }
                        "home"      -> goHome()
                        "power_off" -> {
                            Log.d("TV_POWER", "instance = ${TvAccessibilityService.instance}")
                            TvAccessibilityService.instance?.pressPower(false)
                        }
                        "power_on"  -> {
                            Log.d("TV_POWER", "instance = ${TvAccessibilityService.instance}")
                            TvAccessibilityService.instance?.pressPower(true)
                        }
                        else -> Log.w("FIREBASE", "Unknown action: $action")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FIREBASE", "Database error: ${error.message}")
                }
            })
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private fun goHome() {
        Log.d("APP_LAUNCH", "Going home")
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openApp(appName: String) {
        val key = appName.lowercase().trim()
        val packageName = TV_PACKAGE_MAP[key]
            ?: findPackageByLabel(key)
            ?: run { Log.e("APP_LAUNCH", "No app found: $appName"); return }

        Log.d("APP_LAUNCH", "Launching: $packageName")
        getLaunchIntent(packageName)
            ?.let { launchOrNotify(it, "Opening app", "Tap to open") }
            ?: Log.e("APP_LAUNCH", "No launch intent for: $packageName")
    }

    private fun searchInApp(appName: String, query: String) {
        val encodedQuery = query.replace(" ", "+")
        val uriEncoded   = Uri.encode(query)
        val key = appName.lowercase().trim()

        val intent = when (key) {
            "youtube" -> Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery"))
                .setPackage("com.google.android.youtube.tv")

            "smarttube", "smart tube" -> Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery"))
                .setPackage("com.teamsmart.videomanager.tv")

            "netflix" -> Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.netflix.com/search?q=$encodedQuery"))

            "spotify" -> Intent(Intent.ACTION_VIEW,
                Uri.parse("spotify:search:$query"))

            "brave", "browser" -> Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=$encodedQuery"))
                .setPackage("com.brave.browser")

            "play store" -> Intent(Intent.ACTION_VIEW,
                Uri.parse("market://search?q=$query"))
                .setPackage("com.android.vending")

            "vlc" -> Intent(Intent.ACTION_VIEW, Uri.parse("vlc://$query"))

            "stremio" -> Intent(Intent.ACTION_VIEW,
                Uri.parse("stremio:///search?search=$uriEncoded"))
                .setPackage("com.stremio.one")

            "hotstar", "jio hotstar", "disney", "disney hotstar" ->
                Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.hotstar.com/search?q=$encodedQuery"))
                    .setPackage("in.startv.hotstar")

            else -> { Log.e("APP_SEARCH", "No search handler for: $appName"); return }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        Log.d("APP_SEARCH", "Searching $appName for: $query")
        launchOrNotify(intent, "Searching $appName", "Searching for $query")
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun findPackageByLabel(key: String): String? {
        val apps = packageManager.getInstalledApplications(0)
        return (apps.firstOrNull { packageManager.getApplicationLabel(it).toString().lowercase().trim() == key }
            ?: apps.firstOrNull {
                val label = packageManager.getApplicationLabel(it).toString().lowercase().trim()
                label.contains(key) || key.contains(label)
            })?.packageName
    }

    private fun launchOrNotify(intent: Intent, title: String, text: String) {
        if (Settings.canDrawOverlays(this)) {
            startActivity(intent)
        } else {
            showLaunchNotification(title, text, intent)
        }
    }

    private fun getLaunchIntent(packageName: String): Intent? {
        // Standard launcher
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Leanback (Android TV)
        val leanback = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            `package` = packageName
        }
        packageManager.queryIntentActivities(leanback, 0).firstOrNull()?.activityInfo?.let {
            return Intent(Intent.ACTION_MAIN).setClassName(it.packageName, it.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Any MAIN activity
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).apply { `package` = packageName }, 0
        ).firstOrNull()?.activityInfo?.let {
            return Intent(Intent.ACTION_MAIN).setClassName(it.packageName, it.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return null
    }
}