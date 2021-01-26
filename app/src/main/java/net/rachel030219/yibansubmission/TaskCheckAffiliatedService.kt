package net.rachel030219.yibansubmission

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import org.json.JSONObject

class TaskCheckAffiliatedService: JobIntentService() {
    companion object {
        const val REQUEST_DELAY = 1
        const val REQUEST_SUBMIT = 2
    }
    override fun onHandleWork(intent: Intent) {
        if (intent.hasExtra("request")) {
            when (intent.getIntExtra("request", 0)) {
                REQUEST_DELAY -> {
                    // TODO: 这里三十分钟后启动一次性的 Worker / 设置页，计算起始时间，用户输入 0~23 点 / 设置页，通过 DNS 检查更新
                }
                REQUEST_SUBMIT -> {
                    val locationArray = intent.getStringArrayExtra("location")
                    val temperature = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("KEY_TEMPERATURE")?.toString()?.toFloatOrNull()
                    val ex = intent.getStringExtra("ex")
                    val wfid = intent.getStringExtra("wfid")
                    val title = intent.getStringExtra("title")
                    if (locationArray != null && temperature != null && !ex.isNullOrBlank() && !wfid.isNullOrBlank() && !title.isNullOrBlank()) {
                        // construct data to be submitted
                        val province = locationArray[0]
                        val city = locationArray[1]
                        val county = locationArray[2]
                        val data = "{\"b418fa886b6a38bdce72569a70b1fa10\":\"${temperature}\",\"c77d35b16fb22ec70a1f33c315141dbb\":\"${TimeUtils.getTimeNoSecond()}\",\"2fca911d0600717cc5c2f57fc3702787\":[\"$province\",\"$city\",\"$county\"]}"
                        YibanUtils.submit(data, ex, wfid, object: NetworkTaskListener{
                            val notificationManager = NotificationManagerCompat.from(this@TaskCheckAffiliatedService)
                            override fun onTaskStart() {
                                val submittingNotification = NotificationCompat.Builder(this@TaskCheckAffiliatedService, "TASK").apply {
                                    setSmallIcon(R.drawable.ic_launcher_foreground)
                                    setContentTitle(title)
                                    setContentText(getString(R.string.task_notification_submitting))
                                    setOngoing(true)
                                }.build()
                                notificationManager.notify(233, submittingNotification)
                            }

                            override fun onTaskFinished(jsonObject: JSONObject?) {
                                if (jsonObject?.getInt("code") == 0) {
                                    YibanUtils.getShareUrl(jsonObject.getString("data"), object: NetworkTaskListener{
                                        override fun onTaskStart() {}
                                        override fun onTaskFinished(jsonObject: JSONObject?) {
                                            // copy url to clipboard
                                            val shareURL = jsonObject?.getJSONObject("data")?.getString("uri")
                                            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                                                ClipData.newPlainText("share URL", shareURL))

                                            val submittedNotification = NotificationCompat.Builder(this@TaskCheckAffiliatedService, "TASK").apply {
                                                setSmallIcon(R.drawable.ic_launcher_foreground)
                                                setContentTitle(title)
                                                setContentText(getString(R.string.task_done))
                                                setContentIntent(PendingIntent.getActivity(this@TaskCheckAffiliatedService, 0, Intent(this@TaskCheckAffiliatedService, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
                                                setAutoCancel(true)
                                            }.build()
                                            notificationManager.notify(233, submittedNotification)
                                        }
                                    })
                                } else {
                                    val submitErrorNotification = NotificationCompat.Builder(this@TaskCheckAffiliatedService, "TASK").apply {
                                        setSmallIcon(R.drawable.ic_launcher_foreground)
                                        setContentTitle(getString(R.string.task_done))
                                        setContentText(jsonObject?.getString("msg")?: resources.getString(R.string.unexpected_error))
                                        setContentIntent(PendingIntent.getActivity(this@TaskCheckAffiliatedService, 0, Intent(this@TaskCheckAffiliatedService, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
                                        setAutoCancel(true)
                                    }.build()
                                    notificationManager.notify(233, submitErrorNotification)
                                }
                            }

                        })
                    }
                }
            }
        }
    }
}