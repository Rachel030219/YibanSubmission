package net.rachel030219.yibansubmission

import android.app.PendingIntent
import android.content.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TaskCheckAffiliatedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("TAG", "onReceive:called ")
        if (context != null && intent != null) {
            when (intent.action) {
                "net.rachel030219.yibansubmission.DELAY" -> {
                    val delayedRequest = OneTimeWorkRequestBuilder<TaskCheckWorker>().apply {
                        setInitialDelay(30, TimeUnit.MINUTES)
                        addTag("TASK")
                    }.build()
                    WorkManager.getInstance(context).enqueueUniqueWork("TASK_DELAYED", ExistingWorkPolicy.REPLACE, delayedRequest)
                    NotificationManagerCompat.from(context).cancel(233)
                }
                "net.rachel030219.yibansubmission.SUBMIT" -> {
                    val locationArray = intent.getStringArrayExtra("location")
                    val temperature = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("KEY_TEMPERATURE")?.toString()?.toFloatOrNull()
                    val ex = intent.getStringExtra("ex")
                    val wfid = intent.getStringExtra("wfid")
                    val title = intent.getStringExtra("title")

                    if (locationArray != null && temperature != null && !ex.isNullOrBlank() && !wfid.isNullOrBlank()) {
                        // construct data to be submitted
                        val data = "{\"2d4135d558f849e18a5dcc87b884cce5\":\"${temperature}\",\"c77d35b16fb22ec70a1f33c315141dbb\":\"${TimeUtils.getTimeNoSecond()}\",\"2fca911d0600717cc5c2f57fc3702787\":${
                            locationArray.joinToString(",", "[", "]") { "\"" + it + "\"" }
                        }}"
                        CoroutineScope(Dispatchers.IO).launch {
                            // send notifications
                            val submittingNotification = NotificationCompat.Builder(context, "TASK").apply {
                                setSmallIcon(R.drawable.ic_baseline_playlist_add_check_24)
                                setContentTitle(title)
                                setContentText(context.getString(R.string.task_notification_submitting))
                                setOngoing(true)
                            }.build()
                            NotificationManagerCompat.from(context).notify(233, submittingNotification)
                            val submitResult = YibanUtils.submit(data, ex, wfid)
                            if (title != null) sendFinishNotification(context, title, submitResult)
                        }
                    }
                }
            }
        }
    }

    private fun sendFinishNotification (context: Context, title:String, submitResult: JSONObject?) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (submitResult?.getInt("code") == 0) {
            YibanUtils.getShareUrl(submitResult.getString("data")).also { shareResult ->
                // copy url to clipboard
                val shareURL = shareResult?.getJSONObject("data")?.getString("uri")
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                        ClipData.newPlainText("share URL", shareURL))

                val submittedNotification = NotificationCompat.Builder(context, "TASK").apply {
                    setSmallIcon(R.drawable.ic_baseline_playlist_add_check_24)
                    setContentTitle(title)
                    setContentText(context.getString(R.string.task_done))
                    setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
                    setAutoCancel(true)
                }.build()
                notificationManager.notify(233, submittedNotification)
            }
        } else {
            val submitErrorNotification = NotificationCompat.Builder(context, "TASK").apply {
                setSmallIcon(R.drawable.ic_baseline_playlist_add_check_24)
                setContentTitle(title)
                setContentText(submitResult?.getString("msg")?: context.getString(R.string.unexpected_error))
                setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
                setAutoCancel(true)
            }.build()
            notificationManager.notify(233, submitErrorNotification)
        }
    }
}