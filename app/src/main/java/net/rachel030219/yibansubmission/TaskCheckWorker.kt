package net.rachel030219.yibansubmission

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class TaskCheckWorker (private val context: Context, params: WorkerParameters): CoroutineWorker (context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        YibanUtils.login(object: NetworkTaskListener {
            override fun onTaskStart() {}
            override fun onTaskFinished(jsonObject: JSONObject?) {
                if (jsonObject != null && jsonObject.getInt("response") == 100) {
                    YibanUtils.getHome(object: NetworkTaskListener {
                        override fun onTaskStart() {}
                        override fun onTaskFinished(jsonObject: JSONObject?) {
                            YibanUtils.auth(object: NetworkTaskListener{
                                override fun onTaskStart() {}
                                override fun onTaskFinished(jsonObject: JSONObject?) {
                                    if (jsonObject?.optBoolean("error") != true) {
                                        YibanUtils.getUncompletedList(object: NetworkTaskListener{
                                            override fun onTaskStart() {}
                                            override fun onTaskFinished(jsonObject: JSONObject?) {
                                                // fetch tasks
                                                val tasksList = mutableListOf<JSONObject>()
                                                jsonObject?.optJSONArray("data")?.also { taskJSONArray ->
                                                    for (i in 0 until taskJSONArray.length()) {
                                                        taskJSONArray.getJSONObject(i).also {
                                                            if (it.getString("Title").contains("体温检测"))
                                                                tasksList.add(it)
                                                        }
                                                    }
                                                }
                                                // get the latest one
                                                if (tasksList.size != 0) {
                                                    TimeUtils.descendSort(tasksList.toTypedArray(), "StartTime").also { sortedArray ->
                                                        // variables for notification construction
                                                        val taskTitle = sortedArray[0].getString("Title")
                                                        sortedArray[0].getString("TaskId").let { taskId ->
                                                            val locationPreferences = context.getSharedPreferences("location", Context.MODE_PRIVATE)
                                                            val savedLocation = arrayOf(locationPreferences.getString("province", null), locationPreferences.getString("city", null), locationPreferences.getString("county", null))
                                                            val displayLocation = savedLocation.joinToString()

                                                            // construct notification
                                                            // create notification channel
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                                val taskChannel = NotificationChannel("TASK", context.getString(R.string.task_notification_channel), NotificationManager.IMPORTANCE_DEFAULT)
                                                                if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("TASK_created", false)) {
                                                                    NotificationManagerCompat.from(context).createNotificationChannel(taskChannel)
                                                                    PreferenceManager.getDefaultSharedPreferences(context).edit {
                                                                        putBoolean("TASK_created", true)
                                                                    }
                                                                }
                                                            }
                                                            val taskNotification = NotificationCompat.Builder(context, "TASK").apply {
                                                                setSmallIcon(R.drawable.ic_launcher_foreground)
                                                                setContentTitle(context.getString(R.string.task_notification_title, taskTitle))
                                                                setContentText(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.getString(R.string.task_notification_message_nougat, displayLocation) else context.getString(R.string.task_notification_message))
                                                                setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
                                                                setCategory(NotificationCompat.CATEGORY_REMINDER)
                                                                setAutoCancel(true)
                                                                addAction(NotificationCompat.Action.Builder(null, context.getString(R.string.task_notification_action_delay),
                                                                    PendingIntent.getService(
                                                                        context,
                                                                        1,
                                                                        Intent(context, TaskCheckAffiliatedService::class.java).putExtra("request", TaskCheckAffiliatedService.REQUEST_DELAY),
                                                                        PendingIntent.FLAG_CANCEL_CURRENT)).build())

                                                                // add direct reply action
                                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                                    YibanUtils.getTaskDetail(taskId, object: NetworkTaskListener {
                                                                        override fun onTaskStart() {}
                                                                        override fun onTaskFinished(taskDetail: JSONObject?) {
                                                                            val ex = "{\"TaskId\": \"${taskDetail?.getString("Id")}\", \"title\": \"任务信息\", \"content\": [{\"label\": \"任务名称\", \"value\": \"${taskDetail?.getString("Title")}\"}, {\"label\": \"发布机构\", \"value\": \"${taskDetail?.getString("PubOrgName")}\"}, {\"label\": \"发布人\", \"value\": \"${taskDetail?.getString("PubPersonName")}\"}]}"
                                                                            val wfid = taskDetail?.getString("WFId")
                                                                            val remoteInput = RemoteInput.Builder("KEY_TEMPERATURE").run {
                                                                                setLabel(context.getString(R.string.submit_temperature))
                                                                                build()
                                                                            }
                                                                            val extras2BSent = Bundle().apply {
                                                                                putStringArray("location", savedLocation)
                                                                                putString("ex", ex)
                                                                                putString("wfid", wfid)
                                                                                putString("title", context.getString(R.string.task_notification_title, taskTitle))
                                                                            }
                                                                            addAction(NotificationCompat.Action.Builder(null, context.getString(R.string.task_notification_action_submit),
                                                                                PendingIntent.getService(
                                                                                    context,
                                                                                    2,
                                                                                    Intent(context, TaskCheckAffiliatedService::class.java).putExtra("request", TaskCheckAffiliatedService.REQUEST_SUBMIT).putExtras(extras2BSent),
                                                                                    PendingIntent.FLAG_CANCEL_CURRENT)).addRemoteInput(remoteInput).build())
                                                                        }
                                                                    })
                                                                }
                                                            }.build()
                                                            NotificationManagerCompat.from(context).notify(233, taskNotification)
                                                        }
                                                    }
                                                } else {
                                                    val noTaskNotification = NotificationCompat.Builder(context, "TASK").apply {
                                                        setSmallIcon(R.drawable.ic_launcher_foreground)
                                                        setContentTitle(context.getString(R.string.no_uncompleted_tasks))
                                                        setContentText(context.getString(R.string.task_notification_message))
                                                        setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
                                                        setAutoCancel(true)
                                                    }.build()
                                                    NotificationManagerCompat.from(context).notify(233, noTaskNotification)
                                                }
                                            }
                                        })
                                    }
                                }
                            })
                        }
                    })
                } else {
                    throw IOException("error logging in")
                }
            }
        })
        Result.success()
    }
}