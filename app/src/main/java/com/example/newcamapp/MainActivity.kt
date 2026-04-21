package com.example.newcamapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import java.time.LocalDate
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// データクラスの定義
data class ChalkboardData(
    var taskName: String = "中央区公園及び街路樹等総合維持管理業務\n(中部地区)",
    var workType: String = "",
    var location2: String = "",
    var jvName: String = "南香・高重・蔵田 特定JV",
    var remarkIndex: Int = 0,
    var locationIndex: Int = 0,
    var routeIndex: Int = 0,
    var taskTextSize: Float = 11f
)

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null

    // 修正ポイント: List ではなく MutableList を使用して中身を入れ替え可能にする
    private val boardList: MutableList<ChalkboardData> = MutableList(6) { ChalkboardData() }
    private var currentTabIndex = 0
    private var isUpdatingUI = false

    // スピナー用の配列
    private val remarksArray = arrayOf(" ", "作業前", "作業中", "作業後")
    private val locArray = arrayOf(" ", "大通公園", "創成川公園", "中島公園", "円山公園")
    private val routeArray = arrayOf(" ", "1号線", "2号線", "北1条通", "駅前通")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)

        setupSpinners()
        loadAllData()
        setupTabs()
        setupChalkboardSync()
        setupActionButtons()

        refreshUIFromData()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun setupActionButtons() {
        // リセットボタンの処理
        findViewById<Button>(R.id.btn_reset_current_tab)?.setOnClickListener {
            // boardList が MutableList なので、これで代入可能になります
            boardList[currentTabIndex] = ChalkboardData()
            refreshUIFromData()
            saveData()
            Toast.makeText(this, "タブ ${currentTabIndex + 1} を初期化しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAllData() {
        val prefs = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE)
        for (i in 0..5) {
            val p = "tab_${i}_"
            boardList[i].apply {
                taskName = prefs.getString("${p}taskName", "中央区公園及び街路樹等総合維持管理業務\n(中部地区)") ?: ""
                workType = prefs.getString("${p}workType", "") ?: ""
                location2 = prefs.getString("${p}location2", "") ?: ""
                jvName = prefs.getString("${p}jvName", "南香・高重・蔵田 特定JV") ?: ""
                remarkIndex = prefs.getInt("${p}remarkIndex", 0)
                locationIndex = prefs.getInt("${p}locationIndex", 0)
                routeIndex = prefs.getInt("${p}routeIndex", 0)
                taskTextSize = prefs.getFloat("${p}taskTextSize", 11f)
            }
        }
    }

    private fun saveData() {
        if (isUpdatingUI) return
        val editor = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE).edit()
        val d = boardList[currentTabIndex]
        val p = "tab_${currentTabIndex}_"
        editor.apply {
            putString("${p}taskName", d.taskName)
            putString("${p}workType", d.workType)
            putString("${p}location2", d.location2)
            putString("${p}jvName", d.jvName)
            putInt("${p}remarkIndex", d.remarkIndex)
            putInt("${p}locationIndex", d.locationIndex)
            putInt("${p}routeIndex", d.routeIndex)
            putFloat("${p}taskTextSize", d.taskTextSize)
            apply()
        }
    }

    private fun refreshUIFromData() {
        isUpdatingUI = true
        val d = boardList[currentTabIndex]

        findViewById<EditText>(R.id.edit_task_name_input)?.setText(d.taskName)
        findViewById<EditText>(R.id.edit_work_type_input)?.setText(d.workType)
        findViewById<EditText>(R.id.edit_location2_input)?.setText(d.location2)
        findViewById<EditText>(R.id.edit_jv_name_input)?.setText(d.jvName)

        findViewById<Spinner>(R.id.spinner_remarks_input)?.setSelection(d.remarkIndex)
        findViewById<Spinner>(R.id.spinner_location_input)?.setSelection(d.locationIndex)
        findViewById<Spinner>(R.id.spinner_route_input)?.setSelection(d.routeIndex)

        val rg = findViewById<RadioGroup>(R.id.rg_task_size)
        if (d.taskTextSize == 6f) rg?.check(R.id.rb_size_4) else rg?.check(R.id.rb_size_3)

        updateChalkboardDisplay()
        isUpdatingUI = false
    }

    private fun setupChalkboardSync() {
        val watcher = { action: (String) -> Unit -> createWatcher { if (!isUpdatingUI) { action(it); updateChalkboardDisplay(); saveData() } } }

        findViewById<EditText>(R.id.edit_task_name_input)?.addTextChangedListener(watcher { boardList[currentTabIndex].taskName = it })
        findViewById<EditText>(R.id.edit_work_type_input)?.addTextChangedListener(watcher { boardList[currentTabIndex].workType = it })
        findViewById<EditText>(R.id.edit_location2_input)?.addTextChangedListener(watcher { boardList[currentTabIndex].location2 = it })
        findViewById<EditText>(R.id.edit_jv_name_input)?.addTextChangedListener(watcher { boardList[currentTabIndex].jvName = it })

        findViewById<RadioGroup>(R.id.rg_task_size)?.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdatingUI) {
                boardList[currentTabIndex].taskTextSize = if (checkedId == R.id.rb_size_4) 6f else 11f
                updateChalkboardDisplay()
                saveData()
            }
        }

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingUI) return

                when (parent?.id) {
                    R.id.spinner_remarks_input -> boardList[currentTabIndex].remarkIndex = position
                    R.id.spinner_location_input -> boardList[currentTabIndex].locationIndex = position
                    R.id.spinner_route_input -> boardList[currentTabIndex].routeIndex = position
                }
                updateChalkboardDisplay()
                saveData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Spinner>(R.id.spinner_remarks_input)?.onItemSelectedListener = spinnerListener
        findViewById<Spinner>(R.id.spinner_location_input)?.onItemSelectedListener = spinnerListener
        findViewById<Spinner>(R.id.spinner_route_input)?.onItemSelectedListener = spinnerListener
    }

    private fun updateChalkboardDisplay() {
        val d = boardList[currentTabIndex]
        val offset = d.taskTextSize - 14f

        findViewById<TextView>(R.id.board_task_name_left)?.apply {
            text = d.taskName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (8f + offset).coerceAtLeast(3f))
        }
        findViewById<TextView>(R.id.board_work_type)?.text = d.workType
        findViewById<TextView>(R.id.board_location_2)?.text = d.location2
        findViewById<TextView>(R.id.board_jv_name)?.text = d.jvName

        findViewById<TextView>(R.id.board_remarks)?.text = remarksArray.getOrElse(d.remarkIndex) { "" }.trim()

        val locStr = locArray.getOrElse(d.locationIndex) { "" }.trim()
        val routeStr = routeArray.getOrElse(d.routeIndex) { "" }.trim()
        findViewById<TextView>(R.id.board_location)?.text = "$locStr $routeStr".trim()

        findViewById<TextView>(R.id.board_date)?.text = "R" + JapaneseDate.from(LocalDate.now()).format(DateTimeFormatter.ofPattern("y/MM/dd", Locale.JAPAN))
    }

    private fun setupSpinners() {
        val adapterRemarks = ArrayAdapter(this, android.R.layout.simple_spinner_item, remarksArray)
        adapterRemarks.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinner_remarks_input)?.adapter = adapterRemarks

        val adapterLoc = ArrayAdapter(this, android.R.layout.simple_spinner_item, locArray)
        adapterLoc.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinner_location_input)?.adapter = adapterLoc

        val adapterRoute = ArrayAdapter(this, android.R.layout.simple_spinner_item, routeArray)
        adapterRoute.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinner_route_input)?.adapter = adapterRoute
    }

    private fun setupTabs() {
        findViewById<TabLayout>(R.id.tab_layout)?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabIndex = tab?.position ?: 0
                refreshUIFromData()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val p = f.get()
            val pre = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            imageCapture = ImageCapture.Builder().build()
            p.unbindAll()
            try { p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pre, imageCapture) } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createWatcher(onChanged: (String) -> Unit) = object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { onChanged(s.toString()) }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
}
