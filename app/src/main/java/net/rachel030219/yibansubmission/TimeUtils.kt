package net.rachel030219.yibansubmission

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    fun getToday (): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getTime (): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun getTimeNoSecond (): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }

    fun get14DayAgo (): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -14) }.time)
    }

    fun descendSort (array: Array<JSONObject>, key: String = "FeedbackTime"): Array<JSONObject>{
        if (array.size > 1) {
            for (i in array.indices) {
                for (j in 0 until array.size - i) {
                    if (array[j].getInt(key) < array[j + 1].getInt(key)) {
                        val temp = array[j]
                        array[j] = array[j + 1]
                        array[j + 1] = temp
                    }
                }
            }
        }
        return array
    }
}

fun Int.formatToDate(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this*1000L))
}