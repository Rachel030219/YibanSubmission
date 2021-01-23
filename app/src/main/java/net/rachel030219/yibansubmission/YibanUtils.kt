package net.rachel030219.yibansubmission

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object YibanUtils {
    // basic variables
    private var okHttpClient: OkHttpClient? = null
    private const val CSRF = "RANDOM_VALUE"  // random value
    private val COOKIES = mapOf("csrf_token" to CSRF)  // fixed cookie
    private var COOKIE_SET = "" // dynamic cookie TODO: use CookieJar to replace this
    private val HEADERS = Headers.headersOf("Origin", "https://c.uyiban.com", "User-Agent", "yiban")  // fixed header
    private const val GET = 1
    private const val POST = 2

    // account info
    private var mobileNumber = ""
    private var encryptedPassword = ""
    private var accessToken = ""
    var name = ""
    private var iapp = ""

    // networking
    private val networkScope = CoroutineScope(Dispatchers.IO)

    private suspend fun request (originalUrl: String, method: Int = GET, params: Map<String, String>? = null, headers: Headers = HEADERS, cookies: Map<String, String> = COOKIES) = withContext(Dispatchers.IO) {
        if (okHttpClient == null)
            okHttpClient = OkHttpClient()
        val request = Request.Builder().apply {
            headers(headers)
            addHeader("cookie",
                cookies.toCookieString() + "; $COOKIE_SET"
            )
            when (method) {
                GET -> {
                    url(originalUrl.toHttpUrlOrNull()!!.newBuilder().apply {
                        if (params != null)
                            for ((name, content) in params) {
                                addQueryParameter(name, content)  // convert param map to query params
                            }
                    }.build())
                }
                POST -> {
                    url(originalUrl.toHttpUrlOrNull()!!)
                    if (params != null)
                        post(FormBody.Builder().apply {
                            for ((name, content) in params)
                                add(name, content)
                        }.build())
                }
                else ->
                    return@withContext null
            }
        }.build()
        return@withContext try {
            val response = okHttpClient!!.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val rawData = response.body!!.string()
                if (response.headers["set-cookie"] != null) {
                    COOKIE_SET = response.headers["set-cookie"]!!
                }
                try {
                    JSONObject(rawData)
                } catch (e: JSONException) {
                    JSONObject(mapOf("error" to true, "content" to rawData))
                }
            } else
                null
        } catch (e: IOException) {
            null
        }
    }

    fun initialize (mobileNumber: String, encryptedPassword: String) {
        this.mobileNumber = mobileNumber
        this.encryptedPassword = encryptedPassword
    }

    fun login (taskListener: NetworkTaskListener?) {
        val params = mapOf("mobile" to mobileNumber, "imei" to "0", "password" to EncryptionUtils.decrypt(encryptedPassword, mobileNumber))
        taskListener?.onTaskStart()
        networkScope.launch {
            request("https://mobile.yiban.cn/api/v3/passport/login", params = params)?.also {
                if (it.getInt("response") == 100) {
                    // get and save access token
                    accessToken = it.getJSONObject("data").getJSONObject("user").getString("access_token")
                }
                taskListener?.onTaskFinished(it)
            }
        }
    }

    fun getHome (taskListener: NetworkTaskListener?) {
        val params = mapOf("access_token" to accessToken)
        taskListener?.onTaskStart()
        networkScope.launch {
            request("https://mobile.yiban.cn/api/v3/home", params = params)?.also {
                name = it.getJSONObject("data").getJSONObject("user").getString("userName")
                val appArray = it.getJSONObject("data").getJSONArray("hotApps")
                // iterate through iApps to find the target
                for (i in 0 until appArray.length()) {
                    val appItem = appArray.getJSONObject(i)
                    if (appItem.getString("name") == "易班校本化") {
                        Regex("(iapp.*)\\?").find(appItem.getString("url"))?.groupValues?.get(1)?.also { iappIndex ->
                            iapp = iappIndex
                        }
                    }
                }
                taskListener?.onTaskFinished(it)
            }
        }
    }

    fun auth (taskListener: NetworkTaskListener?) {
        val params = mapOf("act" to iapp, "v" to accessToken)
        networkScope.launch {
            if (okHttpClient == null)
                okHttpClient = OkHttpClient()
            okHttpClient = okHttpClient!!.newBuilder().followRedirects(false).build()
            val location = okHttpClient!!.newCall(Request.Builder().url("https://f.yiban.cn/iapp/index".toHttpUrlOrNull()!!.newBuilder().apply {
                    for ((name, content) in params) {
                        addQueryParameter(name, content)  // convert param map to query params
                    }
                }.build()).build()).execute().headers["Location"]
            if (location == null) {
                taskListener?.onTaskFinished(JSONObject(mapOf("error" to true, "msg" to "Are you authorized to submit data?")))
                return@launch
            }
            val verifyRequest = Regex("verify_request=(.*?)&").find(location)?.groupValues?.get(1)
            request("https://api.uyiban.com/base/c/auth/yiban?verifyRequest=$verifyRequest&CSRF=$CSRF", cookies = COOKIES)?.also { result ->
                val dataURL = result.optJSONObject("data")?.optString("Data")
                if (dataURL.isNullOrBlank()) {
                    // stands for true
                    taskListener?.onTaskFinished(JSONObject(mapOf("result" to true)))
                    return@launch
                } else {
                    val resultHTML = okHttpClient!!.newCall(Request.Builder().apply {
                        url(dataURL.toHttpUrlOrNull()!!)
                        headers(HEADERS)
                        addHeader("cookie",
                                mapOf("loginToken" to accessToken).toCookieString()
                        )
                    }.build()).execute().body?.string()
                    if (resultHTML != null) {
                        val regexResult = Regex("input type=\"hidden\" id=\"(.*?)\" value=\"(.*?)\"").findAll(resultHTML)
                        val postData = mutableMapOf("scope" to "1,2,3,")
                        regexResult.forEach {
                            postData[it.groupValues[1]] = it.groupValues[2]
                        }
                        // usersure result
                        okHttpClient!!.newCall(Request.Builder().apply {
                            url("https://oauth.yiban.cn/code/usersure".toHttpUrlOrNull()!!)
                            post(FormBody.Builder().apply {
                                        for ((name, content) in postData)
                                            add(name, content)
                                    }.build())
                            headers(HEADERS)
                            addHeader("cookie",
                                    mapOf("loginToken" to accessToken).toCookieString()
                            )
                        }.build()).execute().body?.use {
                            if (JSONObject(it.string()).getString("code") == "s200")
                                auth(taskListener)
                            else {
                                // stands for false
                                taskListener?.onTaskFinished(JSONObject(mapOf("result" to false)))
                                return@launch
                            }
                        }
                    }
                }
            }
        }
    }

    fun getUncompletedList (taskListener: NetworkTaskListener?) {
        val params = mapOf("CSRF" to CSRF, "StartTime" to TimeUtils.getToday(), "EndTime" to TimeUtils.getTime())
        taskListener?.onTaskStart()
        networkScope.launch {
            taskListener?.onTaskFinished(request("https://api.uyiban.com/officeTask/client/index/uncompletedList", params = params, cookies = COOKIES))
        }
    }

    fun getCompletedList (taskListener: NetworkTaskListener?) {
        val params = mapOf("CSRF" to CSRF, "StartTime" to TimeUtils.get7DayAgo(), "EndTime" to TimeUtils.getTime())
        taskListener?.onTaskStart()
        networkScope.launch {
            taskListener?.onTaskFinished(request("https://api.uyiban.com/officeTask/client/index/completedList", params = params, cookies = COOKIES))
        }
    }

    fun getJSONByInitiateId (initiateId: String, taskListener: NetworkTaskListener?) {
        val params = mapOf("CSRF" to CSRF)
        taskListener?.onTaskStart()
        networkScope.launch {
            taskListener?.onTaskFinished(request("https://api.uyiban.com/workFlow/c/work/show/view/$initiateId", params = params, cookies = COOKIES))
        }
    }

    fun getTaskDetail (taskId: String, taskListener: NetworkTaskListener?) {
        taskListener?.onTaskStart()
        networkScope.launch {
            taskListener?.onTaskFinished(request("https://api.uyiban.com/officeTask/client/index/detail?TaskId=$taskId&CSRF=$CSRF", cookies = COOKIES))
        }
    }

    fun submit (jsonData: String, jsonExtra: String, wfId: String, taskListener: NetworkTaskListener?) {
        val params = mapOf("data" to jsonData, "extend" to jsonExtra)
        taskListener?.onTaskStart()
        networkScope.launch {
            taskListener?.onTaskFinished(request("https://api.uyiban.com/workFlow/c/my/apply/$wfId?CSRF=$CSRF", method = POST, params = params, cookies = COOKIES))
        }
    }

    fun getShareUrl (initiateId: String, taskListener: NetworkTaskListener?) {
        taskListener?.onTaskStart()
        networkScope.launch {
            taskListener?.onTaskFinished(request("https://api.uyiban.com/workFlow/c/work/share?InitiateId=$initiateId&CSRF=$CSRF", cookies = COOKIES))
        }
    }
}

private fun Map<String, String>.toCookieString(): String {
    return map {
        (name, content) -> "$name=$content"  // convert cookies map to cookies
    }.joinToString(";")
}
