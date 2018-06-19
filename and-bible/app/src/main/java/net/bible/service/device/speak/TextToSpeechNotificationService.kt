package net.bible.service.device.speak

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.annotation.RequiresApi
import de.greenrobot.event.EventBus
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.device.speak.event.SpeakEventManager
import net.bible.service.device.speak.event.SpeakProggressEvent
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@ActivityScope
class TextToSpeechNotificationService: Service() {
    companion object {
        const val ACTION_START="action_start"
        const val ACTION_REMOVE="action_remove"

        const val ACTION_PLAY="action_play"
        const val ACTION_PAUSE="action_pause"
        const val ACTION_REWIND="action_rewind"
        const val ACTION_FAST_FORWARD="action_fast_forward"
        const val ACTION_STOP="action_stop"

        const val CHANNEL_ID="speak-notifications"
        const val NOTIFICATION_ID=1
    }

    @Inject lateinit var speakControl: SpeakControl

    lateinit var notificationManager: NotificationManager

    private val pauseAction: Notification.Action
        get() = generateAction(android.R.drawable.ic_media_pause, getString(R.string.pause), ACTION_PAUSE)

    private val playAction: Notification.Action
        get() = generateAction(android.R.drawable.ic_media_play, getString(R.string.speak), ACTION_PLAY)

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if(! ::speakControl.isInitialized) {
            DaggerActivityComponent.builder()
				.applicationComponent(BibleApplication.getApplication().getApplicationComponent())
				.build().inject(this)
            EventBus.getDefault().register(this)
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            SpeakEventManager.getInstance().addSpeakEventListener { buildNotification(if(it.isSpeaking) pauseAction else playAction) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, getString(R.string.tts_status), NotificationManager.IMPORTANCE_DEFAULT)
                notificationManager.createNotificationChannel(channel)
            }
        }
        handleIntent(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    fun onEventMainThread(ev: SpeakProggressEvent) {
        buildNotification(pauseAction)
    }

    private fun handleIntent(intent: Intent?) {
        if(intent?.action == null) {
            return
        }
        when(intent.action) {
            ACTION_START -> buildNotification(pauseAction)
            ACTION_REMOVE -> removeNotification()
            ACTION_PLAY -> {
                buildNotification(pauseAction)
                speakControl.continueAfterPause()
            }
            ACTION_PAUSE -> {
                buildNotification(playAction)
                speakControl.pause()
            }
            ACTION_FAST_FORWARD -> speakControl.forward()
            ACTION_REWIND -> speakControl.rewind()
            ACTION_STOP -> {
                speakControl.stop()
                removeNotification()
            }
        }
    }

    private fun removeNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        stopService(Intent(applicationContext, this.javaClass))
    }

    private fun generateAction(icon: Int, title: String, intentAction: String): Notification.Action {
        val intent = Intent(applicationContext, this.javaClass)
        intent.setAction(intentAction)
        val pendingIntent = PendingIntent.getService(applicationContext, 1, intent, 0)
        return Notification.Action.Builder(icon, title, pendingIntent).build()
    }

    private fun buildNotification(action: Notification.Action) {
        val style = Notification.MediaStyle()

        val deleteIntent = Intent(applicationContext, this.javaClass)
        deleteIntent.setAction(ACTION_STOP)
        val deletePendingIntent = PendingIntent.getService(applicationContext, 1, deleteIntent, 0)

        val contentIntent = Intent(applicationContext, MainBibleActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val contentPendingIntent = PendingIntent.getActivity(applicationContext, 1, contentIntent, 0)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        builder.setSmallIcon(R.drawable.ichthys_alpha)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(speakControl.getStatusText())
                .setDeleteIntent(deletePendingIntent)
                .setContentIntent(contentPendingIntent)
                .setStyle(style)
                .addAction(generateAction(android.R.drawable.ic_media_rew, getString(R.string.rewind), ACTION_REWIND))
                .addAction(action)
                .addAction(generateAction(android.R.drawable.ic_media_ff, getString(R.string.forward), ACTION_FAST_FORWARD))
                .setOnlyAlertOnce(true)

        style.setShowActionsInCompactView(0, 1, 2)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }
}