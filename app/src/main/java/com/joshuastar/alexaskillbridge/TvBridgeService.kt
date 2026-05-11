package com.joshuastar.alexaskillbridge

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.provider.Settings
class TvBridgeService : Service() {

    private var lastTimestamp: Long = 0
    private val CHANNEL_ID = "tv_bridge_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        listenForCommands()
    }

    private val LAUNCH_CHANNEL_ID = "tv_bridge_launch_channel"

    private fun createNotificationChannel() {
        // Existing low importance channel for persistent notification
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "TV Bridge Service",
            NotificationManager.IMPORTANCE_LOW
        )

        // High importance channel needed for full screen intent to work
        val launchChannel = NotificationChannel(
            LAUNCH_CHANNEL_ID,
            "App Launch",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Used to launch apps via Alexa"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(launchChannel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Alexa TV Bridge")
            .setContentText("Listening for commands...")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .build()
    }
    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.d("APP_LAUNCH", "Going home")
        startActivity(intent)
    }
    private fun searchInApp(appName: String, query: String) {
        val searchName = appName.lowercase().trim()

        val intent = when (searchName) {
            "youtube" -> Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube.tv")
                putExtra("query", query)
            }
            "netflix" -> Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://www.netflix.com/search?q=${query.replace(" ", "+")}")
            }
            "spotify" -> Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("spotify:search:$query")
            }
            "brave", "browser" -> Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://www.google.com/search?q=${query.replace(" ", "+")}")
                setPackage("com.brave.browser")
            }
            "play store" -> Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://search?q=$query")
                setPackage("com.android.vending")
            }
            "vlc" -> Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("vlc://$query")
            }
            else -> {
                Log.e("APP_SEARCH", "Search not supported for: $appName, opening instead")
                openApp(appName)
                return
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.d("APP_SEARCH", "Searching $appName for: $query")

        if (Settings.canDrawOverlays(this)) {
            startActivity(intent)
        } else {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(this, LAUNCH_CHANNEL_ID)
                .setContentTitle("Searching $appName")
                .setContentText("Searching for $query")
                .setSmallIcon(R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setFullScreenIntent(pendingIntent, true)
                .build()
            getSystemService(NotificationManager::class.java).notify(2, notification)
        }
    }
    private fun listenForCommands() {
        val ref = FirebaseDatabase
            .getInstance()
            .getReference("tv_commands/current")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val action = snapshot.child("action").getValue(String::class.java)
                val app = snapshot.child("app").getValue(String::class.java)
                val query = snapshot.child("query").getValue(String::class.java) ?: ""
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0

                if (timestamp == lastTimestamp) return
                lastTimestamp = timestamp

                when (action) {
                    "open" -> if (app != null) openApp(app)
                    "search" -> if (app != null) searchInApp(app, query)
                    "home" -> goHome()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FIREBASE", "Database Error: ${error.message}")
            }
        })
    }

    private val tvPackageMap = mapOf(
        "youtube" to "com.google.android.youtube.tv",
        "youtube music" to "com.google.android.youtube.tvmusic",
        "netflix" to "com.netflix.ninja",
        "prime video" to "com.amazon.amazonvideo.livingroom",
        "prime" to "com.amazon.amazonvideo.livingroom",
        "hotstar" to "in.startv.hotstar",
        "disney" to "in.startv.hotstar",
        "disney hotstar" to "in.startv.hotstar",
        "spotify" to "com.spotify.tv.android",
        "vlc" to "org.videolan.vlc",
        "stremio" to "com.stremio.one",
        "brave" to "com.brave.browser",
        "localsend" to "org.localsend.localsend_app",
        "mx player" to "com.mxtech.videoplayer.television",
        "anydesk" to "com.anydesk.anydeskandroid",
        "play store" to "com.android.vending",
        "manorama" to "com.mmtv.manoramamax.android",
        "settings" to "com.android.tv.settings"
    )

    private fun openApp(appName: String) {
        val searchName = appName.lowercase().trim()

        // 1. Check hardcoded map first
        val hardcodedPackage = tvPackageMap[searchName]
        if (hardcodedPackage != null) {
            Log.d("APP_LAUNCH", "Hardcoded match: $hardcodedPackage")
            launchByPackage(hardcodedPackage)
            return
        }

        // 2. Search all installed apps by label
        val installedApps = packageManager.getInstalledApplications(0)
        val matchedPackage = installedApps.firstOrNull { appInfo ->
            val label = packageManager.getApplicationLabel(appInfo).toString().lowercase().trim()
            label == searchName
        } ?: installedApps.firstOrNull { appInfo ->
            val label = packageManager.getApplicationLabel(appInfo).toString().lowercase().trim()
            label.contains(searchName) || searchName.contains(label)
        }

        if (matchedPackage == null) {
            Log.e("APP_LAUNCH", "No app found matching: $appName")
            return
        }

        Log.d("APP_LAUNCH", "Label match: ${matchedPackage.packageName}")
        launchByPackage(matchedPackage.packageName)
    }

    private fun launchByPackage(packageName: String) {
        val launchIntent = getLaunchIntent(packageName)

        if (launchIntent == null) {
            Log.e("APP_LAUNCH", "No launch intent for: $packageName")
            return
        }

        if (Settings.canDrawOverlays(this)) {
            startActivity(launchIntent)
            Log.d("APP_LAUNCH", "Launching: $packageName")
        } else {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(this, LAUNCH_CHANNEL_ID)
                .setContentTitle("Opening app")
                .setContentText("Tap to open")
                .setSmallIcon(R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setFullScreenIntent(pendingIntent, true)
                .build()
            getSystemService(NotificationManager::class.java).notify(2, notification)
        }
    }

    private fun getLaunchIntent(packageName: String): Intent? {
        // Try standard launcher intent
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            return it.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        // Try LEANBACK launcher (Android TV apps)
        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            `package` = packageName
        }
        val leanbackActivities = packageManager.queryIntentActivities(leanbackIntent, 0)
        if (leanbackActivities.isNotEmpty()) {
            val activity = leanbackActivities.first().activityInfo
            return Intent(Intent.ACTION_MAIN).apply {
                setClassName(activity.packageName, activity.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        // Try any MAIN activity in the package
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            `package` = packageName
        }
        val mainActivities = packageManager.queryIntentActivities(mainIntent, 0)
        if (mainActivities.isNotEmpty()) {
            val activity = mainActivities.first().activityInfo
            return Intent(Intent.ACTION_MAIN).apply {
                setClassName(activity.packageName, activity.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        return null
    }
}