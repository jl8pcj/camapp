package com.example.newcamapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
import java.time.LocalDate
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class ChalkboardData(
    var taskName: String = "中央区公園及び街路樹等総合維持管理業務\n(中部地区)",
    var workType: String = "",
    var location: String = "",
    var route: String = "",
    var location2: String = "",
    var jvName: String = "南香・高重・蔵田 特定JV",
    var remarkIndex: Int = 0,
    var taskTextSize: Float = 5f
)

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null

    private val boardList = List(6) { ChalkboardData() }
    private var currentTabIndex = 0
    private var isUpdatingUI = false

    private val dirPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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
        refreshUIFromData()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun setupActionButtons() {
        findViewById<View>(resources.getIdentifier("btn_settings", "id", packageName))?.setOnClickListener { dirPickerLauncher.launch(null) }

        // ImageButtonとして取得
        findViewById<ImageButton>(resources.getIdentifier("btn_gallery", "id", packageName))?.setOnClickListener { openGallery() }

        findViewById<View>(resources.getIdentifier("btn_capture", "id", packageName))?.setOnClickListener { takePhoto() }
        findViewById<Button>(resources.getIdentifier("btn_reset_current_tab", "id", packageName))?.setOnClickListener { resetCurrentTabData() }
    }

    private fun resetCurrentTabData() {
        boardList[currentTabIndex].apply {
            taskName = "中央区公園及び街路樹等総合維持管理業務\n(中部地区)"
            workType = ""
            location = ""
            route = ""
            location2 = ""
            jvName = "南香・高重・蔵田 特定JV"
            remarkIndex = 0
            taskTextSize = 5f
        }
        refreshUIFromData()
        saveData()
        Toast.makeText(this, "タブ ${currentTabIndex + 1} を初期化しました", Toast.LENGTH_SHORT).show()
    }

    private fun openGallery() {
        val folderUriString = getSavedFolderPath() ?: return Toast.makeText(this, "保存先を設定してください", Toast.LENGTH_SHORT).show()
        // 既存のファイラー起動から、自作のGalleryActivity起動に変更
        val intent = Intent(this, GalleryActivity::class.java).apply {
            putExtra("folder_uri", folderUriString)
        }
        startActivity(intent)
    }

    private fun takePhoto() {
        val folderUriString = getSavedFolderPath() ?: return Toast.makeText(this, "保存先を設定してください", Toast.LENGTH_SHORT).show()
        val now = Date()
        val folderName = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val fileName = "IMG_${timeStamp}.jpg"

        val rootDir = DocumentFile.fromTreeUri(this, Uri.parse(folderUriString)) ?: return
        var dateDir = rootDir.findFile(folderName) ?: rootDir.createDirectory(folderName)
        val file = dateDir?.createFile("image/jpeg", fileName) ?: return

        val previewBitmap = viewFinder.bitmap ?: return
        val boardView = findViewById<View>(resources.getIdentifier("mini_board", "id", packageName)) ?: return
        val boardBitmap = Bitmap.createBitmap(boardView.width, boardView.height, Bitmap.Config.ARGB_8888)
        boardView.draw(Canvas(boardBitmap))

        val resultBitmap = previewBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(boardBitmap, (resultBitmap.width - boardView.width - 24).toFloat(), (resultBitmap.height - boardView.height - 24).toFloat(), null)

        try {
            contentResolver.openOutputStream(file.uri)?.use {
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)

                // ★ 撮影した画像をサムネイルとしてボタンにセット
                runOnUiThread {
                    val galleryBtn = findViewById<ImageButton>(resources.getIdentifier("btn_gallery", "id", packageName))
                    galleryBtn?.setImageBitmap(resultBitmap)
                }

                Toast.makeText(this, "$folderName 内に保存しました", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "保存エラー", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAllData() {
        val prefs = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE)
        for (i in 0..5) {
            val p = "tab_${i}_"
            boardList[i].apply {
                taskName = prefs.getString("${p}taskName", "中央区公園及び街路樹等総合維持管理業務\n(中部地区)") ?: ""
                workType = prefs.getString("${p}workType", "") ?: ""
                location = prefs.getString("${p}location", "") ?: ""
                route = prefs.getString("${p}route", "") ?: ""
                location2 = prefs.getString("${p}location2", "") ?: ""
                jvName = prefs.getString("${p}jvName", "南香・高重・蔵田 特定JV") ?: ""
                remarkIndex = prefs.getInt("${p}remarkIndex", 0)
                taskTextSize = prefs.getFloat("${p}taskTextSize", 5f)
            }
        }
    }

    private fun saveData() {
        if (isUpdatingUI) return
        val editor = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE).edit()
        val d = boardList[currentTabIndex]
        val p = "tab_${currentTabIndex}_"
        editor.putString("${p}taskName", d.taskName).putString("${p}workType", d.workType)
            .putString("${p}location", d.location).putString("${p}route", d.route)
            .putString("${p}location2", d.location2).putString("${p}jvName", d.jvName)
            .putInt("${p}remarkIndex", d.remarkIndex).putFloat("${p}taskTextSize", d.taskTextSize).apply()
    }

    private fun refreshUIFromData() {
        isUpdatingUI = true
        val d = boardList[currentTabIndex]
        findViewById<EditText>(resources.getIdentifier("edit_task_name_input", "id", packageName))?.setText(d.taskName)
        findViewById<EditText>(resources.getIdentifier("edit_work_type_input", "id", packageName))?.setText(d.workType)
        findViewById<EditText>(resources.getIdentifier("edit_location2_input", "id", packageName))?.setText(d.location2)
        findViewById<EditText>(resources.getIdentifier("edit_jv_name_input", "id", packageName))?.setText(d.jvName)
        findViewById<Spinner>(resources.getIdentifier("spinner_remarks_input", "id", packageName))?.setSelection(d.remarkIndex)

        val rg = findViewById<RadioGroup>(resources.getIdentifier("rg_task_size", "id", packageName))
        when (d.taskTextSize) {
            3f -> rg?.check(resources.getIdentifier("rb_size_3", "id", packageName))
            4f -> rg?.check(resources.getIdentifier("rb_size_4", "id", packageName))
            else -> rg?.check(resources.getIdentifier("rb_size_5", "id", packageName))
        }
        updateChalkboardDisplay()
        isUpdatingUI = false
    }

    private fun setupChalkboardSync() {
        val watcher = { action: (String) -> Unit -> createWatcher { if (!isUpdatingUI) { action(it); updateChalkboardDisplay(); saveData() } } }
        findViewById<EditText>(resources.getIdentifier("edit_task_name_input", "id", packageName))?.addTextChangedListener(watcher { boardList[currentTabIndex].taskName = it })
        findViewById<EditText>(resources.getIdentifier("edit_work_type_input", "id", packageName))?.addTextChangedListener(watcher { boardList[currentTabIndex].workType = it })
        findViewById<EditText>(resources.getIdentifier("edit_location2_input", "id", packageName))?.addTextChangedListener(watcher { boardList[currentTabIndex].location2 = it })
        findViewById<EditText>(resources.getIdentifier("edit_jv_name_input", "id", packageName))?.addTextChangedListener(watcher { boardList[currentTabIndex].jvName = it })

        findViewById<RadioGroup>(resources.getIdentifier("rg_task_size", "id", packageName))?.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdatingUI) {
                boardList[currentTabIndex].taskTextSize = when (resources.getResourceEntryName(checkedId)) {
                    "rb_size_3" -> 3f
                    "rb_size_4" -> 4f
                    else -> 5f
                }
                updateChalkboardDisplay()
                saveData()
            }
        }
    }

    private fun updateChalkboardDisplay() {
        val d = boardList[currentTabIndex]
        val taskView = findViewById<TextView>(resources.getIdentifier("board_task_name_left", "id", packageName))
        taskView?.apply {
            text = d.taskName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, d.taskTextSize)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        findViewById<TextView>(resources.getIdentifier("board_work_type", "id", packageName))?.text = d.workType
        findViewById<TextView>(resources.getIdentifier("board_location_2", "id", packageName))?.text = d.location2
        findViewById<TextView>(resources.getIdentifier("board_jv_name", "id", packageName))?.text = d.jvName
        findViewById<TextView>(resources.getIdentifier("board_remarks", "id", packageName))?.text = arrayOf("", "作業前", "作業中", "作業後")[d.remarkIndex]
        findViewById<TextView>(resources.getIdentifier("board_location", "id", packageName))?.text = if (d.location.isNotEmpty() || d.route.isNotEmpty()) "${d.location} / ${d.route}".trim(' ', '/') else ""
        findViewById<TextView>(resources.getIdentifier("board_date", "id", packageName))?.text = "R" + JapaneseDate.from(LocalDate.now()).format(DateTimeFormatter.ofPattern("y/MM/dd", Locale.JAPAN))
    }

    private fun setupSpinners() {
        val r = arrayOf("", "あかしあ公園", "山麓公園", "どんぐり公園", "日新公園", "やちだも公園", "さくらんぼ公園", "北円山公園", "北４条かすみ公園", "南7条りんりん公園", "南１０条明星公園", "北４条まどか公園", "円山裏参道公園", "南６条西２２丁目広場", "南１条西１８丁目広場", "南２条みゆき公園", "南１４条あゆみ公園")
        val l = arrayOf("", "北５条線", "北４条線", "北３条線", "北２条線", "北１条線", "北大通線", "南大通線", "西２０丁目線", "西２１丁目線", "西２３丁目線", "西２４丁目線", "西２５丁目線")
        val m = arrayOf("", "作業前", "作業中", "作業後")

        val ls = findViewById<Spinner>(resources.getIdentifier("spinner_location_input", "id", packageName))
        val rs = findViewById<Spinner>(resources.getIdentifier("spinner_route_input", "id", packageName))
        val ms = findViewById<Spinner>(resources.getIdentifier("spinner_remarks_input", "id", packageName))

        ls?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, l)
        rs?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, r)
        ms?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, m)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingUI) return
                when (resources.getResourceEntryName(p?.id ?: 0)) {
                    "spinner_location_input" -> boardList[currentTabIndex].location = p?.getItemAtPosition(pos).toString()
                    "spinner_route_input" -> boardList[currentTabIndex].route = p?.getItemAtPosition(pos).toString()
                    "spinner_remarks_input" -> boardList[currentTabIndex].remarkIndex = pos
                }
                updateChalkboardDisplay(); saveData()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        ls?.onItemSelectedListener = listener; rs?.onItemSelectedListener = listener; ms?.onItemSelectedListener = listener
    }

    private fun setupTabs() {
        findViewById<TabLayout>(resources.getIdentifier("tab_layout", "id", packageName))?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { currentTabIndex = tab?.position ?: 0; refreshUIFromData() }
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
            p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pre, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createWatcher(onChanged: (String) -> Unit) = object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { onChanged(s.toString()) }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
    private fun saveFolderPath(u: String) = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE).edit().putString("save_folder_uri", u).apply()
    private fun getSavedFolderPath() = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE).getString("save_folder_uri", null)
}