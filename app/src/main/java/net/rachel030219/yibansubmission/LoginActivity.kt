package net.rachel030219.yibansubmission

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewAnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.hypot

class LoginActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // read saved account and password
        val preferences = PreferenceManager.getDefaultSharedPreferences(this@LoginActivity)
        val mobileFromPref = preferences.getString("mobile", null)
        val passwordFromPref = preferences.getString("password", null)
        // automatically fill in data
        if (!mobileFromPref.isNullOrBlank() && !passwordFromPref.isNullOrBlank()) {
            login_mobile.text!!.append(mobileFromPref)
            login_password.text!!.append(EncryptionUtils.decrypt(passwordFromPref, mobileFromPref))
        }

        login_button.setOnClickListener {
            // reveal animation
            val animX = (login_button.x + login_button.width / 2).toInt()
            val animY = (login_button.y + login_button.height / 2).toInt()
            val finalRadius = hypot(animX.toDouble(), animY.toDouble()).toFloat()
            val anim = ViewAnimationUtils.createCircularReveal(login_progress_layout, animX, animY, 0f, finalRadius)
            anim.duration = 200
            anim.doOnStart {
                login_progress_layout.visibility = View.VISIBLE
                login_progress_text.text = resources.getString(R.string.loading)
            }
            anim.doOnEnd {
                login_form_layout.visibility = View.GONE
            }
            anim.start()

            // save the password
            val mobile = login_mobile.text.toString()
            val password = EncryptionUtils.encrypt(login_password.text.toString(), login_mobile.text.toString())

            // process logging in
            YibanUtils.initialize(mobile, password)
            YibanUtils.login(object: NetworkTaskListener {
                override fun onTaskStart() {}
                override fun onTaskFinished(jsonObject: JSONObject?) {
                    CoroutineScope(Dispatchers.Main).launch {
                        if (jsonObject == null) {
                            login_progress_text.text = resources.getString(R.string.unexpected_error)
                        } else {
                            login_progress_text.text = jsonObject.getString("message")
                            login_progress.visibility = View.INVISIBLE
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (jsonObject.getInt("response") == 100) {
                                    startActivity(Intent(this@LoginActivity, TasksActivity::class.java))
                                    // store account and password
                                    if (preferences.getString("mobile", null) != mobile) {
                                        preferences.edit().putString("mobile", mobile).apply()
                                    }
                                    if (preferences.getString("password", null) != password) {
                                        preferences.edit().putString("password", password).apply()
                                    }
                                    finish()
                                } else {
                                    val endAnimX = login_progress_layout.width / 2
                                    val endAnimY = login_progress_layout.height / 2
                                    val endInitialRadius = hypot(endAnimX.toDouble(), endAnimY.toDouble()).toFloat()
                                    val endAnim = ViewAnimationUtils.createCircularReveal(login_progress_layout, endAnimX, endAnimY, endInitialRadius, 0f)
                                    endAnim.doOnStart {
                                        login_form_layout.visibility = View.VISIBLE
                                    }
                                    endAnim.doOnEnd {
                                        login_progress_layout.visibility = View.GONE
                                    }
                                    endAnim.start()
                                }
                            }, 1000)
                        }
                    }
                }
            })
        }

        // respond to enter
        login_password.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                login_button.callOnClick()
                true
            } else false
        }
    }
}