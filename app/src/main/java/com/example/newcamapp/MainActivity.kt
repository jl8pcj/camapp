package com.example.newcamapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ★ 初期値をすべて 3f に統一
data class ChalkboardData(
    var taskName: String = "中央区公園及び街路樹等総合維持管理業務\n(中部地区)",
    var workType: String = "",
    var location: String = "",
    var route: String = "",
    var location2: String = "",
    var jvName: String = "南香・高重・蔵田 特定JV",
    var remarkIndex: Int = 0,

    var sizeTaskName: Float = 5f,
    var sizeWorkType: Float = 6f,
    var sizeLocation: Float = 5f,
    var sizeRoute: Float = 5f,
    var sizeLocation2: Float = 5f,
    var sizeRemarks: Float = 11f,
    var sizeJVName: Float = 5f,
    var sizeDate: Float = 7f
)

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null

    private val boardList = List(6) { ChalkboardData() }
    private var currentTabIndex = 0
    private var isUpdatingUI = false

    private val dirPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                saveFolderPath(it.toString())
                Toast.makeText(this, "保存先を変更しました", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)

        loadAllData()
        setupTabs()
        setupSpinners()
        setupChalkboardSync()
        setupActionButtons()

        // ★ 最初のタブに合わせてサイズボタンをセット
        setupSizeButtons()

        refreshUIFromData()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    private fun setupActionButtons() {
        findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            dirPickerLauncher.launch(null)
        }

        findViewById<ImageButton>(R.id.btn_gallery)?.setOnClickListener {
            val folderUriString = getSavedFolderPath()
            if (folderUriString == null) {
                Toast.makeText(this, "先に設定から保存先を選択してください", Toast.LENGTH_LONG).show()
            } else {
                val intent = Intent(this, GalleryActivity::class.java)
                intent.putExtra("folder_uri", folderUriString)
                startActivity(intent)
            }
        }

        findViewById<View>(R.id.btn_capture)?.setOnClickListener {
            hideKeyboard()
            Handler(Looper.getMainLooper()).postDelayed({ takePhoto() }, 300)
        }

        findViewById<Button>(R.id.btn_reset_current_tab)?.setOnClickListener {
            resetCurrentTabData()
        }
    }

    private fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    private fun takePhoto() {
        val folderUriString = getSavedFolderPath()
            ?: return Toast.makeText(this, "保存先を設定してください", Toast.LENGTH_SHORT).show()

        val now = Date()
        val folderName = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val fileName = "IMG_${timeStamp}.jpg"

        val rootDir = DocumentFile.fromTreeUri(this, Uri.parse(folderUriString)) ?: return
        val dateDir = rootDir.findFile(folderName) ?: rootDir.createDirectory(folderName)
        val file = dateDir?.createFile("image/jpeg", fileName) ?: return

        val previewBitmap = viewFinder.bitmap ?: return
        val boardView = findViewById<View>(R.id.mini_board) ?: return

        val boardBitmap =
            Bitmap.createBitmap(boardView.width, boardView.height, Bitmap.Config.ARGB_8888)
        boardView.draw(Canvas(boardBitmap))

        val resultBitmap = previewBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        canvas.drawBitmap(
            boardBitmap,
            (resultBitmap.width - boardView.width - 24).toFloat(),
            (resultBitmap.height - boardView.height - 24).toFloat(),
            null
        )

        try {
            contentResolver.openOutputStream(file.uri)?.use {
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                runOnUiThread {
                    findViewById<ImageButton>(R.id.btn_gallery)?.setImageBitmap(resultBitmap)
                }
                Toast.makeText(this, "保存しました: $fileName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Save error", e)
        }
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun loadAllData() {
        val prefs = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE)
        for (i in 0..5) {
            val p = "tab_${i}_"
            boardList[i].apply {
                taskName = prefs.getString("${p}taskName", taskName) ?: taskName
                workType = prefs.getString("${p}workType", "") ?: ""
                location = prefs.getString("${p}location", "") ?: ""
                route = prefs.getString("${p}route", "") ?: ""
                location2 = prefs.getString("${p}location2", "") ?: ""
                jvName = prefs.getString("${p}jvName", jvName) ?: jvName
                remarkIndex = prefs.getInt("${p}remarkIndex", 0)

                // ★ 保存されていなければ初期値 3f を使う
                sizeTaskName = prefs.getFloat("${p}sizeTaskName", 3f)
                sizeWorkType = prefs.getFloat("${p}sizeWorkType", 3f)
                sizeLocation = prefs.getFloat("${p}sizeLocation", 3f)
                sizeRoute = prefs.getFloat("${p}sizeRoute", 3f)
                sizeLocation2 = prefs.getFloat("${p}sizeLocation2", 3f)
                sizeRemarks = prefs.getFloat("${p}sizeRemarks", 3f)
                sizeJVName = prefs.getFloat("${p}sizeJVName", 3f)
                sizeDate = prefs.getFloat("${p}sizeDate", 3f)
            }
        }
    }

    private fun saveData() {
        if (isUpdatingUI) return
        val d = boardList[currentTabIndex]
        val p = "tab_${currentTabIndex}_"
        val editor = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE).edit()

        editor.putString("${p}taskName", d.taskName)
        editor.putString("${p}workType", d.workType)
        editor.putString("${p}location", d.location)
        editor.putString("${p}route", d.route)
        editor.putString("${p}location2", d.location2)
        editor.putString("${p}jvName", d.jvName)
        editor.putInt("${p}remarkIndex", d.remarkIndex)

        editor.putFloat("${p}sizeTaskName", d.sizeTaskName)
        editor.putFloat("${p}sizeWorkType", d.sizeWorkType)
        editor.putFloat("${p}sizeLocation", d.sizeLocation)
        editor.putFloat("${p}sizeRoute", d.sizeRoute)
        editor.putFloat("${p}sizeLocation2", d.sizeLocation2)
        editor.putFloat("${p}sizeRemarks", d.sizeRemarks)
        editor.putFloat("${p}sizeJVName", d.sizeJVName)
        editor.putFloat("${p}sizeDate", d.sizeDate)

        editor.apply()
    }

    private fun refreshUIFromData() {
        isUpdatingUI = true
        val d = boardList[currentTabIndex]

        findViewById<EditText>(R.id.edit_task_name_input)?.setText(d.taskName)
        findViewById<EditText>(R.id.edit_work_type_input)?.setText(d.workType)
        findViewById<EditText>(R.id.edit_location2_input)?.setText(d.location2)
        findViewById<EditText>(R.id.edit_jv_name_input)?.setText(d.jvName)
        findViewById<Spinner>(R.id.spinner_remarks_input)?.setSelection(d.remarkIndex)

        updateChalkboardDisplay()
        isUpdatingUI = false
    }
    private fun setupChalkboardSync() {
        val watcher = { action: (String) -> Unit ->
            createWatcher {
                if (!isUpdatingUI) {
                    action(it)
                    updateChalkboardDisplay()
                    saveData()
                }
            }
        }

        findViewById<EditText>(R.id.edit_task_name_input)
            ?.addTextChangedListener(watcher { boardList[currentTabIndex].taskName = it })

        findViewById<EditText>(R.id.edit_work_type_input)
            ?.addTextChangedListener(watcher { boardList[currentTabIndex].workType = it })

        findViewById<EditText>(R.id.edit_location2_input)
            ?.addTextChangedListener(watcher { boardList[currentTabIndex].location2 = it })

        findViewById<EditText>(R.id.edit_jv_name_input)
            ?.addTextChangedListener(watcher { boardList[currentTabIndex].jvName = it })
    }

    // ★ タブ切り替えごとに正しく動くサイズボタン
    private fun setupSizeButtons() {

        fun setup(idUp: Int, idDown: Int, getter: () -> Float, setter: (Float) -> Unit) {
            findViewById<Button>(idUp)?.setOnClickListener {
                setter(getter() + 1f)
                updateChalkboardDisplay()
                saveData()
            }
            findViewById<Button>(idDown)?.setOnClickListener {
                setter((getter() - 1f).coerceAtLeast(3f))
                updateChalkboardDisplay()
                saveData()
            }
        }

        // ★ getter/setter で毎回 currentTabIndex を参照する
        setup(R.id.btn_size_task_up, R.id.btn_size_task_down,
            { boardList[currentTabIndex].sizeTaskName },
            { boardList[currentTabIndex].sizeTaskName = it })

        setup(R.id.btn_size_work_up, R.id.btn_size_work_down,
            { boardList[currentTabIndex].sizeWorkType },
            { boardList[currentTabIndex].sizeWorkType = it })

        setup(R.id.btn_size_location_up, R.id.btn_size_location_down,
            { boardList[currentTabIndex].sizeLocation },
            { boardList[currentTabIndex].sizeLocation = it })

        setup(R.id.btn_size_route_up, R.id.btn_size_route_down,
            { boardList[currentTabIndex].sizeRoute },
            { boardList[currentTabIndex].sizeRoute = it })

        setup(R.id.btn_size_location2_up, R.id.btn_size_location2_down,
            { boardList[currentTabIndex].sizeLocation2 },
            { boardList[currentTabIndex].sizeLocation2 = it })

        setup(R.id.btn_size_remarks_up, R.id.btn_size_remarks_down,
            { boardList[currentTabIndex].sizeRemarks },
            { boardList[currentTabIndex].sizeRemarks = it })

        setup(R.id.btn_size_jv_up, R.id.btn_size_jv_down,
            { boardList[currentTabIndex].sizeJVName },
            { boardList[currentTabIndex].sizeJVName = it })

        setup(R.id.btn_size_date_up, R.id.btn_size_date_down,
            { boardList[currentTabIndex].sizeDate },
            { boardList[currentTabIndex].sizeDate = it })
    }

    private fun updateChalkboardDisplay() {
        val d = boardList[currentTabIndex]

        findViewById<TextView>(R.id.board_task_name_left)?.apply {
            text = d.taskName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, d.sizeTaskName)
        }

        findViewById<TextView>(R.id.board_work_type)?.apply {
            text = d.workType
            setTextSize(TypedValue.COMPLEX_UNIT_SP, d.sizeWorkType)
        }

        findViewById<TextView>(R.id.board_location)?.apply {
            text = if (d.location.isNotEmpty() || d.route.isNotEmpty())
                "${d.location} / ${d.route}".trim(' ', '/')
            else ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, d.sizeLocation)
        }

        findViewById<TextView>(R.id.board_location_2)?.apply {
            text = d.location2
            setTextSize(TypedValue.COMPLEX_UNIT_SP, d.sizeLocation2)
        }

        findViewById<TextView>(R.id.board_remarks)?.apply {
            text = arrayOf("作業前", "作業中", "作業後", "")[d.remarkIndex]
            setTextSize(TypedValue.COMPLEX_UNIT_SP, d.sizeRemarks)
        }

        findViewById<TextView>(R.id.board_jv_name)?.apply {
            text = d.jvName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, d.sizeJVName)
        }

        findViewById<TextView>(R.id.board_date)?.apply {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val warekiYear = year - 2018
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            text = String.format(Locale.JAPAN, "R%d/%02d/%02d", warekiYear, month, day)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, d.sizeDate)
        }
    }

    private fun setupSpinners() {
        val r = arrayOf("", "あかしあ公園", "山麓公園", "どんぐり公園", "日新公園", "やちだも公園", "さくらんぼ公園", "北円山公園", "北４条かすみ公園", "南7条りんりん公園", "南１０条明星公園", "北４条まどか公園", "円山裏参道公園", "南６条西２２丁目広場", "南１条西１８丁目広場", "南２条みゆき公園", "南１４条あゆみ公園")
        val l = arrayOf("位置を選択", "", "北５条線", "北４条線", "北３条線", "北２条線", "北１条線", "北大通線", "南大通線", "西２０丁目線", "西２１丁目線", "西２３丁目線", "西２４丁目線", "西２５丁目線")
        val m = arrayOf("作業前", "作業中", "作業後", "")

        val ls = findViewById<Spinner>(R.id.spinner_location_input)
        val rs = findViewById<Spinner>(R.id.spinner_route_input)
        val ms = findViewById<Spinner>(R.id.spinner_remarks_input)

        ls?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, l)
        rs?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, r)
        ms?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, m)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingUI) return
                when (p?.id) {
                    R.id.spinner_location_input -> boardList[currentTabIndex].location =
                        p.getItemAtPosition(pos).toString()

                    R.id.spinner_route_input -> boardList[currentTabIndex].route =
                        p.getItemAtPosition(pos).toString()

                    R.id.spinner_remarks_input -> boardList[currentTabIndex].remarkIndex = pos
                }
                updateChalkboardDisplay()
                saveData()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        ls?.onItemSelectedListener = listener
        rs?.onItemSelectedListener = listener
        ms?.onItemSelectedListener = listener
    }

    private fun setupTabs() {
        findViewById<TabLayout>(R.id.tab_layout)?.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    currentTabIndex = tab?.position ?: 0
                    refreshUIFromData()

                    // ★ タブ切り替え時にサイズボタンを再設定
                    setupSizeButtons()
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
    }

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val provider = f.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    // ★ 初期化時も 3f に統一
    private fun resetCurrentTabData() {
        boardList[currentTabIndex].apply {
            taskName = "中央区公園及び街路樹等総合維持管理業務\n(中部地区)"
            workType = ""
            location = ""
            route = ""
            location2 = ""
            jvName = "南香・高重・蔵田 特定JV"
            remarkIndex = 0

            sizeTaskName = 5f
            sizeWorkType = 6f
            sizeLocation = 5f
            sizeRoute = 5f
            sizeLocation2 = 5f
            sizeRemarks = 11f
            sizeJVName = 5f
            sizeDate = 7f


        }

        refreshUIFromData()
        saveData()
        Toast.makeText(this, "リセットしました", Toast.LENGTH_SHORT).show()
    }

    private fun createWatcher(onChanged: (String) -> Unit) =
        object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                onChanged(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

    private fun saveFolderPath(u: String) =
        getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("save_folder_uri", u)
            .apply()

    private fun getSavedFolderPath(): String? =
        getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE)
            .getString("save_folder_uri", null)
}
