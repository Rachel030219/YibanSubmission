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
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.Duration
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TaskCheckWorker (private val context: Context, params: WorkerParameters): CoroutineWorker (context, params) {
    override suspend fun doWork(): Result = coroutineScope {
        withContext(Dispatchers.IO) {
            val loginResult = YibanUtils.login()
            if (loginResult?.getInt("response") == 100) {
                YibanUtils.getHome()
                val authResult = YibanUtils.auth()
                if (authResult?.optBoolean("error") == false) {
                    val listResult = YibanUtils.getUncompletedList()
                    // fetch tasks
                    val tasksList = mutableListOf<JSONObject>()
                    listResult?.optJSONArray("data")?.also { taskJSONArray ->
                        for (i in 0 until taskJSONArray.length()) {
                            taskJSONArray.getJSONObject(i).also {
                                if (it.getString("Title").contains("体温检测"))
                                    tasksList.add(it)
                            }
                        }
                    }
                    // get the latest one
                    if (tasksList.size != 0) {
                        val sortedArray = TimeUtils.descendSort(tasksList.toTypedArray(), "StartTime")
                            // variables for notification construction
                            val taskTitle = sortedArray[0].getString("Title")
                            sortedArray[0].getString("TaskId").let { taskId ->
                                val locationPreferences = context.getSharedPreferences("location", Context.MODE_PRIVATE)
                                val savedLocation = arrayOf(locationPreferences.getString("province", null), locationPreferences.getString("city", null), locationPreferences.getString("county", null))
                                val displayLocation = savedLocation.joinToString()

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

                                val extras2BSent = Bundle()
                                val remoteInput = RemoteInput.Builder("KEY_TEMPERATURE").run {
                                    setLabel(context.getString(R.string.submit_temperature))
                                    build()
                                }
                                val taskDetail = YibanUtils.getTaskDetail(taskId)?.optJSONObject("data")
                                // construct direct reply action
                                val ex = "{\"TaskId\": \"${taskDetail?.getString("Id")}\", \"title\": \"任务信息\", \"content\": [{\"label\": \"任务名称\", \"value\": \"${taskDetail?.getString("Title")}\"}, {\"label\": \"发布机构\", \"value\": \"${taskDetail?.getString("PubOrgName")}\"}, {\"label\": \"发布人\", \"value\": \"${taskDetail?.getString("PubPersonName")}\"}]}"
                                val wfid = taskDetail?.getString("WFId")
                                extras2BSent.apply {
                                    putStringArray("location", savedLocation)
                                    putString("ex", ex)
                                    putString("wfid", wfid)
                                    putString("title", context.getString(R.string.task_notification_title, taskTitle))
                                }

                                // construct notification
                                val taskNotification = NotificationCompat.Builder(context, "TASK").apply {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.task_notification_message_nougat, displayLocation)))
                                    } else {
                                        setContentText(context.getString(R.string.task_notification_message))
                                    }
                                    setSmallIcon(R.drawable.ic_baseline_playlist_add_check_24)
                                    setContentTitle(context.getString(R.string.task_notification_title, taskTitle))
                                    setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
                                    setCategory(NotificationCompat.CATEGORY_REMINDER)
                                    setAutoCancel(true)
                                    // add direct reply action
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        addAction(NotificationCompat.Action.Builder(null, context.getString(R.string.task_notification_action_submit),
                                                PendingIntent.getBroadcast(
                                                        context,
                                                        1,
                                                        Intent(context, TaskCheckAffiliatedReceiver::class.java).apply { action = "net.rachel030219.yibansubmission.SUBMIT"; putExtras(extras2BSent) },
                                                        PendingIntent.FLAG_CANCEL_CURRENT)).addRemoteInput(remoteInput).build())
                                    }
                                    addAction(NotificationCompat.Action.Builder(null, context.getString(R.string.task_notification_action_delay),
                                            PendingIntent.getBroadcast(
                                                    context,
                                                    2,
                                                    Intent(context, TaskCheckAffiliatedReceiver::class.java).apply { action = "net.rachel030219.yibansubmission.DELAY" },
                                                    PendingIntent.FLAG_CANCEL_CURRENT)).build())
                                }.build()
                                NotificationManagerCompat.from(context).notify(233, taskNotification)
                                scheduleTask(context)
                                Result.success()
                            }
                    } else {
                        val noTaskNotification = NotificationCompat.Builder(context, "TASK").apply {
                            setSmallIcon(R.drawable.ic_baseline_playlist_add_check_24)
                            setContentTitle(context.getString(R.string.no_uncompleted_tasks))
                            setContentText(context.getString(R.string.task_notification_message))
                            setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
                            setAutoCancel(true)
                        }.build()
                        NotificationManagerCompat.from(context).notify(233, noTaskNotification)
                        scheduleTask(context)
                        Result.success()
                    }
                } else
                    signalError()
            } else
                signalError()
        }
    }

    private fun scheduleTask (context: Context, delayMinutes: Long? = null) {
        val hour = PreferenceManager.getDefaultSharedPreferences(context).getString("task_check_time", "10").toString().toIntOrNull() ?: 10
        val finalHour = if (hour > 23) 23 else if (hour < 0) 0 else hour
        val delay = delayMinutes ?: Duration(DateTime.now(), DateTime.now().withTimeAtStartOfDay().plusDays(1).plusHours(finalHour)).standardMinutes
        val recursiveRequest = OneTimeWorkRequestBuilder<TaskCheckWorker>().apply {
            setInitialDelay(delay, TimeUnit.MINUTES)
            setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            addTag("TASK")
        }.build()
        WorkManager.getInstance(context).enqueueUniqueWork("TASK", ExistingWorkPolicy.REPLACE, recursiveRequest)
    }

    private fun signalError (): Result {
        val errorNotification = NotificationCompat.Builder(context, "TASK").apply {
            setSmallIcon(R.drawable.ic_baseline_playlist_add_check_24)
            setContentTitle(context.getString(R.string.task_notification_error))
            setContentText(context.getString(R.string.task_notification_message))
            setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, TasksActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }, PendingIntent.FLAG_CANCEL_CURRENT))
            setAutoCancel(true)
        }.build()
        NotificationManagerCompat.from(context).notify(233, errorNotification)
        scheduleTask(context, 30)
        return Result.retry()
    }
}