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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class TasksActivity: AppCompatActivity() {
    var mData: MutableList<Task>? = null
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadData () {
        tasks_recycler.visibility = View.GONE
        tasks_hint.visibility = View.GONE
        tasks_progress_text.text = resources.getString(R.string.loading)
        tasks_progress_layout.visibility = View.VISIBLE
        YibanUtils.getHome(object: NetworkTaskListener{
            override fun onTaskStart() {}
            override fun onTaskFinished(jsonObject: JSONObject?) {
                YibanUtils.auth(object: NetworkTaskListener{
                    override fun onTaskStart() {}
                    override fun onTaskFinished(jsonObject: JSONObject?) {
                        if (jsonObject?.optBoolean("error") == true) {
                            CoroutineScope(Dispatchers.Main).launch {
                                tasks_hint.text = jsonObject.optString("msg")?: resources.getString(R.string.unexpected_error)
                                tasks_hint.visibility = View.VISIBLE
                            }
                        } else {
                            val tasksFetchListener = object : NetworkTaskListener {
                                override fun onTaskStart() {
                                }

                                override fun onTaskFinished(jsonObject: JSONObject?) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        tasks_progress_layout.visibility = View.INVISIBLE
                                        jsonObject?.optJSONArray("data")?.also {
                                            mData = mutableListOf()
                                            if (it.length() > 0)
                                                for (i in 0 until it.length()) {
                                                    val uncompletedTask = it.getJSONObject(i)
                                                    if (uncompletedTask.getString("Title").contains("体温检测"))
                                                        mData!!.add(Task(uncompletedTask.getString("Title"), uncompletedTask.getInt("StartTime").formatToDate(), uncompletedTask.getInt("EndTime").formatToDate(), uncompletedTask))
                                                }
                                        }
                                        if (mData != null && mData!!.size > 0) {
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
                            }
                            CoroutineScope(Dispatchers.Main).launch {
                                title = if (showingUncompleted) {
                                    YibanUtils.getUncompletedList(tasksFetchListener)
                                    resources.getString(R.string.uncompleted_label, YibanUtils.name)
                                } else {
                                    YibanUtils.getCompletedList(tasksFetchListener)
                                    resources.getString(R.string.completed_label, YibanUtils.name)
                                }
                            }
                        }
                    }
                })
            }
        })
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
        targetHolder.itemCard.cardElevation = dpToPx(2.0f, this)
        targetHolder.itemDivider.visibility = View.GONE
        targetHolder.itemProgress.visibility = View.GONE
        targetHolder.itemSubmitLayout.visibility = View.GONE
        targetHolder.itemIndicator.rotation = 0f
    }
    
    inner class Adapter: RecyclerView.Adapter<Holder> () {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(this@TasksActivity).inflate(R.layout.item_tasks, parent, false))
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            if (mData != null) {
                holder.itemTitleText.text = mData!![position].title
                holder.itemStartTimeText.text = resources.getString(R.string.start_time, mData!![position].startTime)
                holder.itemEndTimeText.text = resources.getString(R.string.end_time, mData!![position].endTime)

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
                                mData!![position].rawJSONData?.optString("TaskId")?.let { taskID ->
                                    YibanUtils.getTaskDetail(taskID, object : NetworkTaskListener {
                                        override fun onTaskStart() {}
                                        override fun onTaskFinished(jsonObject: JSONObject?) {
                                            // initialize extra data
                                            val taskDetail = jsonObject?.getJSONObject("data")
                                            val ex = "{\"TaskId\": \"${taskDetail?.getString("Id")}\", \"title\": \"任务信息\", \"content\": [{\"label\": \"任务名称\", \"value\": \"${taskDetail?.getString("Title")}\"}, {\"label\": \"发布机构\", \"value\": \"${taskDetail?.getString("PubOrgName")}\"}, {\"label\": \"发布人\", \"value\": \"${taskDetail?.getString("PubPersonName")}\"}]}"

                                            CoroutineScope(Dispatchers.Main).launch {
                                                // auto fiil in location
                                                val locationPreferences = getSharedPreferences("location", MODE_PRIVATE)
                                                val provinceFromPrefs = locationPreferences.getString("province", null)
                                                val cityFromPrefs = locationPreferences.getString("city", null)
                                                val countyFromPrefs = locationPreferences.getString("county", null)

                                                if (!locationPreferences.getString("province", null).isNullOrBlank())
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
                                                        val data = "{\"b418fa886b6a38bdce72569a70b1fa10\":\"${holder.itemSubmitTemperature.text}\",\"c77d35b16fb22ec70a1f33c315141dbb\":\"${TimeUtils.getTimeNoSecond()}\",\"2fca911d0600717cc5c2f57fc3702787\":[\"$province\",\"$city\",\"$county\"]}"

                                                        // submit data
                                                        YibanUtils.submit(data, ex, taskDetail?.getString("WFId")?: "Null", object: NetworkTaskListener{
                                                            override fun onTaskStart() {}
                                                            override fun onTaskFinished(jsonObject: JSONObject?) {
                                                                if (jsonObject?.getInt("code") == 0) {
                                                                    YibanUtils.getShareUrl(jsonObject.getString("data"), object: NetworkTaskListener{
                                                                        override fun onTaskStart() {}
                                                                        override fun onTaskFinished(jsonObject: JSONObject?) {
                                                                            // copy url to clipboard
                                                                            val shareURL = jsonObject?.getJSONObject("data")?.getString("uri")
                                                                            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("share URL", shareURL))

                                                                            // show result, then collapse it
                                                                            showResult(true, resources.getString(R.string.task_done), holder)
                                                                            Handler(mainLooper).postDelayed({
                                                                                collapseResult(holder)
                                                                                expanded = false
                                                                                mData!!.removeAt(position)
                                                                                recyclerAdapter?.notifyItemRemoved(position)
                                                                                loadData()
                                                                            }, 1000)
                                                                        }
                                                                    })
                                                                } else {
                                                                    showResult(false, jsonObject?.getString("msg")?: resources.getString(R.string.unexpected_error), holder)
                                                                    Handler(mainLooper).postDelayed({
                                                                        holder.itemHintText.visibility = View.GONE
                                                                        holder.itemSubmitLayout.visibility = View.VISIBLE
                                                                    }, 1000)
                                                                }
                                                            }
                                                        })
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
                                    })
                                }
                                initialized = true
                            }
                            expanded = true
                        }
                    }
                } else {
                    holder.itemCard.setOnClickListener {
                        holder.itemCard.cardElevation = dpToPx(4.0f, this@TasksActivity)
                        holder.itemDivider.visibility = View.VISIBLE
                        holder.itemProgress.visibility = View.VISIBLE
                        mData!![position].rawJSONData?.optString("TaskId")?.let { taskID ->
                            YibanUtils.getTaskDetail(taskID, object: NetworkTaskListener {
                                override fun onTaskStart() {}
                                override fun onTaskFinished(jsonObject: JSONObject?) {
                                    // initialize extra data
                                    val taskDetail = jsonObject?.getJSONObject("data")
                                    // get share url by string InitiateId
                                    if (taskDetail != null) {
                                        YibanUtils.getShareUrl(taskDetail.getString("InitiateId"), object: NetworkTaskListener {
                                            override fun onTaskStart() {}

                                            override fun onTaskFinished(jsonObject: JSONObject?) {
                                                val shareURL = jsonObject?.getJSONObject("data")?.getString("uri")
                                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shareURL)))
                                            }
                                        })
                                        CoroutineScope(Dispatchers.Main).launch {
                                            holder.itemCard.cardElevation = dpToPx(2.0f, this@TasksActivity)
                                            holder.itemProgress.visibility = View.GONE
                                            holder.itemDivider.visibility = View.GONE
                                        }
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
        override fun getItemCount(): Int {
            return mData?.size ?: 0
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