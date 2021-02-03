package net.rachel030219.yibansubmission

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.activity_tasks.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.*

class TasksActivity: AppCompatActivity() {
    var mData: MutableList<Task> = mutableListOf()
    var recyclerAdapter: Adapter? = null
    var showingUncompleted = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)
        recyclerAdapter = Adapter()
        tasks_recycler.apply {
            layoutManager = LinearLayoutManager(this@TasksActivity)
            adapter = recyclerAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.tasks_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.tasks_menu_switch -> {
                showingUncompleted = !showingUncompleted
                loadData()
                true
            }
            R.id.tasks_menu_info -> {
                val targetURL = if (Locale.getDefault().language == Locale.CHINESE.language) "https://github.com/Rachel030219/YibanSubmission/blob/master/README_CN.md" else "https://github.com/Rachel030219/YibanSubmission/blob/master/README.md"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetURL)))
                true
            }
            R.id.tasks_menu_settings -> {
                startActivity(Intent(this, TaskPreferenceActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadData () {
        tasks_recycler.invalidate()
        tasks_recycler.visibility = View.GONE
        tasks_hint.visibility = View.GONE
        tasks_progress_text.text = resources.getString(R.string.loading)
        tasks_progress_layout.visibility = View.VISIBLE
        GlobalScope.launch {
            YibanUtils.getHome()
            val authResult = YibanUtils.auth()
            if (authResult?.optBoolean("error") != true) {
                withContext(Dispatchers.Main) {
                    title = if (showingUncompleted) {
                        processResult(YibanUtils.getUncompletedList())
                        resources.getString(R.string.uncompleted_label, YibanUtils.name)
                    } else {
                        processResult(YibanUtils.getCompletedList())
                        resources.getString(R.string.completed_label, YibanUtils.name)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    tasks_progress_layout.visibility = View.INVISIBLE
                    tasks_hint.text = authResult.optString("msg")?: resources.getString(R.string.unexpected_error)
                    tasks_hint.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun processResult (jsonObject: JSONObject?) {
        CoroutineScope(Dispatchers.Main).launch {
            tasks_progress_layout.visibility = View.INVISIBLE
            jsonObject?.optJSONArray("data")?.also {
                mData.clear()
                if (it.length() > 0)
                    for (i in 0 until it.length()) {
                        val taskItem = it.getJSONObject(i)
                        if (taskItem.getString("Title").contains("体温检测"))
                            mData.add(Task(taskItem.getString("Title"), taskItem.getInt("StartTime").formatToDate(), taskItem.getInt("EndTime").formatToDate(), taskItem))
                    }
            }
            if (mData.isNotEmpty()) {
                // update RecyclerView
                recyclerAdapter?.notifyDataSetChanged()
                tasks_recycler.visibility = View.VISIBLE
            } else {
                // set hint text
                if (jsonObject?.optBoolean("error", false) == true)
                    tasks_hint.text = jsonObject.optString("content")
                else if (jsonObject?.optInt("code") != 0)
                    tasks_hint.text = jsonObject?.optString("msg")
                            ?: resources.getString(R.string.unexpected_error)
                else
                    tasks_hint.text = if (showingUncompleted) resources.getString(R.string.no_uncompleted_tasks) else resources.getString(R.string.no_completed_tasks)
                tasks_hint.visibility = View.VISIBLE
            }
        }
    }

    private fun showResult (success: Boolean, message: String, targetHolder: Holder) {
        CoroutineScope(Dispatchers.Main).launch {
            if (success)
                targetHolder.itemHintText.setTextColor(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) resources.getColor(R.color.success_text, theme) else resources.getColor(R.color.success_text))
            else
                targetHolder.itemHintText.setTextColor(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) resources.getColor(R.color.primary_text, theme) else resources.getColor(R.color.primary_text))
            targetHolder.itemHintText.text = message
            targetHolder.itemSubmitLayout.visibility = View.GONE
            targetHolder.itemHintText.visibility = View.VISIBLE
        }
    }
    
    private fun collapseResult (targetHolder: Holder) {
        CoroutineScope(Dispatchers.Main).launch {
            targetHolder.itemCard.cardElevation = dpToPx(2.0f, this@TasksActivity)
            targetHolder.itemDivider.visibility = View.GONE
            targetHolder.itemProgress.visibility = View.GONE
            targetHolder.itemSubmitLayout.visibility = View.GONE
            targetHolder.itemIndicator.rotation = 0f
        }
    }
    
    inner class Adapter: RecyclerView.Adapter<Holder> () {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(this@TasksActivity).inflate(R.layout.item_tasks, parent, false))
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            if (mData.isNotEmpty()) {
                holder.itemTitleText.text = mData[position].title
                holder.itemStartTimeText.text = resources.getString(R.string.start_time, mData[position].startTime)
                holder.itemEndTimeText.text = resources.getString(R.string.end_time, mData[position].endTime)

                // expansion animation and data fetching
                if (showingUncompleted) {
                    var initialized = false
                    var expanded = false
                    holder.itemCard.setOnClickListener {
                        if (expanded) {
                            collapseResult(holder)
                            expanded = false
                        } else {
                            holder.itemCard.cardElevation = dpToPx(4.0f, this@TasksActivity)
                            holder.itemDivider.visibility = View.VISIBLE
                            holder.itemIndicator.rotation = -90f
                            if (initialized) {
                                holder.itemSubmitLayout.visibility = View.VISIBLE
                            } else {
                                holder.itemProgress.visibility = View.GONE
                                mData[position].rawJSONData?.optString("TaskId")?.let { taskID ->
                                    GlobalScope.launch {
                                        val jsonDetailResult = YibanUtils.getTaskDetail(taskID)
                                        // initialize extra data
                                        val taskDetail = jsonDetailResult?.getJSONObject("data")
                                        val ex = "{\"TaskId\": \"${taskDetail?.getString("Id")}\", \"title\": \"任务信息\", \"content\": [{\"label\": \"任务名称\", \"value\": \"${taskDetail?.getString("Title")}\"}, {\"label\": \"发布机构\", \"value\": \"${taskDetail?.getString("PubOrgName")}\"}, {\"label\": \"发布人\", \"value\": \"${taskDetail?.getString("PubPersonName")}\"}]}"

                                        withContext(Dispatchers.Main) {
                                            // auto fill in location
                                            val locationPreferences = getSharedPreferences("location", MODE_PRIVATE)
                                            val provinceFromPrefs = locationPreferences.getString("province", null)
                                            val cityFromPrefs = locationPreferences.getString("city", null)
                                            val countyFromPrefs = locationPreferences.getString("county", null)
                                            if (!provinceFromPrefs.isNullOrBlank())
                                                holder.itemSubmitLocationProvince.apply { text = null ; append(provinceFromPrefs) }
                                            if (!cityFromPrefs.isNullOrBlank())
                                                holder.itemSubmitLocationCity.apply { text = null ; append(cityFromPrefs) }
                                            if (!countyFromPrefs.isNullOrBlank())
                                                holder.itemSubmitLocationCounty.apply { text = null ; append(countyFromPrefs) }

                                            holder.itemProgress.visibility = View.GONE
                                            holder.itemSubmitLayout.visibility = View.VISIBLE
                                            holder.itemSubmitButton.setOnClickListener {
                                                val province = holder.itemSubmitLocationProvince.text
                                                val city = holder.itemSubmitLocationCity.text
                                                val county = holder.itemSubmitLocationCounty.text
                                                if (!province.isNullOrBlank() && !city.isNullOrBlank() && !county.isNullOrBlank()) {
                                                    // save location
                                                    locationPreferences.edit().apply {
                                                        if (provinceFromPrefs != province.toString()) putString("province", province.toString())
                                                        if (cityFromPrefs != city.toString()) putString("city", city.toString())
                                                        if (countyFromPrefs != county.toString()) putString("county", county.toString())
                                                        apply()
                                                    }

                                                    // construct data to be submitted
                                                    val data = "{\"2d4135d558f849e18a5dcc87b884cce5\":\"${holder.itemSubmitTemperature.text}\",\"c77d35b16fb22ec70a1f33c315141dbb\":\"${TimeUtils.getTimeNoSecond()}\",\"2fca911d0600717cc5c2f57fc3702787\":[\"$province\",\"$city\",\"$county\"]}"

                                                    // submit data
                                                    launch(Dispatchers.IO) {
                                                        val submitResult = YibanUtils.submit(data, ex, taskDetail?.getString("WFId")?: "Null")
                                                        if (submitResult?.getInt("code") == 0) {
                                                            val shareResult = YibanUtils.getShareUrl(submitResult.getString("data"))
                                                            // copy url to clipboard
                                                            val shareURL = shareResult?.getJSONObject("data")?.getString("uri")
                                                            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("share URL", shareURL))

                                                            // show result, then collapse it
                                                            showResult(true, resources.getString(R.string.task_done), holder)
                                                            Handler(mainLooper).postDelayed({
                                                                collapseResult(holder)
                                                                expanded = false
                                                                mData.removeAt(position)
                                                                recyclerAdapter?.notifyItemRemoved(position)
                                                                loadData()
                                                            }, 1000)
                                                        } else {
                                                            showResult(false, submitResult?.getString("msg")?: resources.getString(R.string.unexpected_error), holder)
                                                            Handler(mainLooper).postDelayed({
                                                                holder.itemHintText.visibility = View.GONE
                                                                holder.itemSubmitLayout.visibility = View.VISIBLE
                                                            }, 1000)
                                                        }
                                                    }
                                                } else {
                                                    showResult(false, resources.getString(R.string.task_location_error), holder)
                                                    Handler(mainLooper).postDelayed({
                                                            holder.itemHintText.visibility = View.GONE
                                                            holder.itemSubmitLayout.visibility = View.VISIBLE
                                                        }, 1000)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            initialized = true
                        }
                        expanded = true
                    }
                } else {
                    holder.itemCard.setOnClickListener {
                        holder.itemCard.cardElevation = dpToPx(4.0f, this@TasksActivity)
                        holder.itemDivider.visibility = View.VISIBLE
                        holder.itemProgress.visibility = View.VISIBLE
                        mData[position].rawJSONData?.optString("TaskId")?.let { taskID ->
                            YibanUtils.getTaskDetail(taskID)?.getJSONObject("data").also { taskDetail ->
                                // get share url by string InitiateId
                                if (taskDetail != null) {
                                    val shareURL = YibanUtils.getShareUrl(taskDetail.getString("InitiateId"))?.getJSONObject("data")?.getString("uri")
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shareURL)))
                                }
                            }
                        }
                    }
                }
            }
        }
        override fun onViewRecycled(holder: Holder) {
            super.onViewRecycled(holder)
            collapseResult(holder)
        }
        override fun getItemCount(): Int {
            return mData.size
        }
    }
    class Holder(layout: View): RecyclerView.ViewHolder (layout) {
        val itemCard: CardView = layout.findViewById(R.id.task_card)
        val itemTitleText: TextView = layout.findViewById(R.id.task_title)
        val itemStartTimeText: TextView = layout.findViewById(R.id.task_start)
        val itemEndTimeText: TextView = layout.findViewById(R.id.task_end)
        val itemSubmitLayout: LinearLayout = layout.findViewById(R.id.task_submit_layout)
        val itemSubmitTemperature: TextInputEditText = layout.findViewById(R.id.task_submit_temperature)
        val itemSubmitLocationProvince: TextInputEditText = layout.findViewById(R.id.task_submit_location_province)
        val itemSubmitLocationCity: TextInputEditText = layout.findViewById(R.id.task_submit_location_city)
        val itemSubmitLocationCounty: TextInputEditText = layout.findViewById(R.id.task_submit_location_county)
        val itemSubmitButton: MaterialButton = layout.findViewById(R.id.task_submit)
        val itemProgress: ProgressBar = layout.findViewById(R.id.task_progress)
        val itemDivider: View = layout.findViewById(R.id.task_divider)
        val itemHintText: TextView = layout.findViewById(R.id.task_hint)
        val itemIndicator: ImageView = layout.findViewById(R.id.task_indicator)
    }

    data class Task (val title: String, val startTime: String, val endTime: String, val rawJSONData: JSONObject?)
    
    // metrics converting
    fun dpToPx (dp: Float, context: Context): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
    }
}