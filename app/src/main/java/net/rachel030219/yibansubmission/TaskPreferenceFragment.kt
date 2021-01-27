package net.rachel030219.yibansubmission

import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.joda.time.DateTime
import org.joda.time.Duration
import java.util.concurrent.TimeUnit

class TaskPreferenceFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference, rootKey)
        findPreference<SwitchPreferenceCompat>("task_check")?.setOnPreferenceChangeListener { _, newValue ->
            val hour = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("task_check_time", "10").toString().toIntOrNull() ?: 10
            if (newValue == true)
                scheduleTask(hour)
            else
                cancelTask(hour)
            true
        }
        findPreference<EditTextPreference>("task_check_time")?.setOnPreferenceChangeListener { _, newValue ->
            val hour = newValue.toString().toIntOrNull() ?: 10
            if (PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("task_check", false))
                scheduleTask(hour)
            else
                cancelTask(hour)
            true
        }
    }

    private fun scheduleTask (hour: Int) {
        val delay = if (DateTime.now().hourOfDay < hour) {
            Duration(DateTime.now(), DateTime.now().withTimeAtStartOfDay().plusHours(hour)).standardMinutes
        } else {
            Duration(DateTime.now(), DateTime.now().withTimeAtStartOfDay().plusDays(1).plusHours(hour)).standardMinutes
        }
        val periodicWorkRequest = PeriodicWorkRequestBuilder<TaskCheckWorker>(24, TimeUnit.HOURS, PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS, TimeUnit.MILLISECONDS).apply {
            setInitialDelay(delay, TimeUnit.MINUTES)
            addTag("TASK")
        }.build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork("TASK", ExistingPeriodicWorkPolicy.REPLACE, periodicWorkRequest)
        Toast.makeText(requireActivity(), getString(R.string.preference_scheduled_added, hour.toString()), Toast.LENGTH_SHORT).show()
    }

    private fun cancelTask (hour: Int) {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("TASK")
        Toast.makeText(requireActivity(), getString(R.string.preference_scheduled_cancelled, hour.toString()), Toast.LENGTH_SHORT).show()
    }
}