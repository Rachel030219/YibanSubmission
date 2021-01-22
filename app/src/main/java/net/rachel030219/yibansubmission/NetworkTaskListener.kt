package net.rachel030219.yibansubmission

import org.json.JSONObject

interface NetworkTaskListener {
    fun onTaskStart ()
    fun onTaskFinished (jsonObject: JSONObject?)
}