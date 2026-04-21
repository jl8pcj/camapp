package com.example.newcamapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
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

// 黒板のデータを保持するデータクラス
data class ChalkboardData(
    var taskName: String = "中央区公園及び街路樹等総合維持管理業務\n(中部地区)",
    var workType: String = "",
    var location: String = "",
    var route: String = "",
    var location2: String = "",
    var jvName: String = "南香・高重・蔵田 特定JV",
    var remarkIndex: Int = 0,
    var taskTextSize: Float = 14f
)

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null

    // 6つのタブ分のデータを保持
    private val boardList: MutableList<ChalkboardData> = MutableList(6) { ChalkboardData() }
    private var currentTabIndex = 0
    private var isUpdatingUI = false

    // 黒板の基準フォントサイズ
    private val BASE_SIZE_TASK = 14f
    private val BASE_SIZE_WORK = 13f
    private val BASE_SIZE_LOC2 = 13f
    private val BASE_SIZE_JV = 11f
    private val BASE_SIZE_REMARK = 12f
    private val BASE_SIZE_LOC = 12f
    private val BASE_SIZE_DATE = 11f

    // 保存先フォルダ選択用のランチャー
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

        // カメラ権限の確認
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }

        // データの読み込みと初期設定
        loadAllData()
        setupTabs()
        setupSpinners()
        setupChalkboardSync()
        setupActionButtons()

        // 最初の表示を更新
        refreshUIFromData()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun setupActionButtons() {
        // 設定ボタン（保存先選択）
        findViewById<View>(R.id.btn_settings)?.setOnClickListener { dirPickerLauncher.launch(null) }

        // ギャラリーボタン
        findViewById<ImageButton>(R.id.btn_gallery)?.setOnClickListener { openGallery() }

        // 撮影ボタン
        findViewById<View>(R.id.btn_capture)?.setOnClickListener { takePhoto() }

        // リセットボタン（現在のタブのみ初期化）
        findViewById<Button>(R.id.btn_reset_current_tab)?.setOnClickListener {
            boardList[currentTabIndex] = ChalkboardData()
            refreshUIFromData()
            saveData()
            Toast.makeText(this, "タブ ${currentTabIndex + 1} を初期化しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val folderUriString = getSavedFolderPath() ?: return Toast.makeText(this, "保存先を設定してください", Toast.LENGTH_SHORT).show()
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
        val dateDir = rootDir.findFile(folderName) ?: rootDir.createDirectory(folderName)
        val file = dateDir?.createFile("image/jpeg", fileName) ?: return

        val previewBitmap = viewFinder.bitmap ?: return
        val boardView = findViewById<View>(R.id.mini_board) ?: return

        // 黒板部分をビットマップ化
        val boardBitmap = Bitmap.createBitmap(boardView.width, boardView.height, Bitmap.Config.ARGB_8888)
        boardView.draw(Canvas(boardBitmap))

        // 写真と黒板を合成
        val resultBitmap = previewBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(boardBitmap, (resultBitmap.width - boardView.width - 24).toFloat(), (resultBitmap.height - boardView.height - 24).toFloat(), null)

        try {
            contentResolver.openOutputStream(file.uri)?.use {
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                runOnUiThread {
                    // 最新の写真をサムネイルとして表示
                    findViewById<ImageButton>(R.id.btn_gallery)?.setImageBitmap(resultBitmap)
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
                taskTextSize = prefs.getFloat("${p}taskTextSize", 14f)
            }
        }
    }

    private fun saveData() {
        if (isUpdatingUI) return
        val editor = getSharedPreferences("chalkboard_prefs", Context.MODE_PRIVATE).edit()
        val d = boardList[currentTabIndex]
        val p = "tab_${currentTabIndex}_"
        editor.putString("${p}taskName", d.taskName)
            .putString("${p}workType", d.workType)
            .putString("${p}location", d.location)
            .putString("${p}route", d.route)
            .putString("${p}location2", d.location2)
            .putString("${p}jvName", d.jvName)
            .putInt("${p}remarkIndex", d.remarkIndex)
            .putFloat("${p}taskTextSize", d.taskTextSize)
            .apply()
    }

    private fun refreshUIFromData() {
        isUpdatingUI = true
        val d = boardList[currentTabIndex]
        findViewById<EditText>(R.id.edit_task_name_input)?.setText(d.taskName)
        findViewById<EditText>(R.id.edit_work_type_input)?.setText(d.workType)
        findViewById<EditText>(R.id.edit_location2_input)?.setText(d.location2)
        findViewById<EditText>(R.id.edit_jv_name_input)?.setText(d.jvName)
        findViewById<Spinner>(R.id.spinner_remarks_input)?.setSelection(d.remarkIndex)

        // ラジオグループのチェック状態復元
        val rg = findViewById<RadioGroup>(R.id.rg_task_size)
        when (d.taskTextSize) {
            12f -> rg?.check(R.id.rb_size_3) // 「小」として扱う例
            13f -> rg?.check(R.id.rb_size_4) // 「標準」として扱う例
            else -> rg?.check(R.id.rb_size_3)
        }

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
                boardList[currentTabIndex].taskTextSize = when (checkedId) {
                    R.id.rb_size_3 -> 14f // 標準
                    R.id.rb_size_4 -> 12f // 小
                    else -> 14f
                }
                updateChalkboardDisplay()
                saveData()
            }
        }
    }

    private fun updateChalkboardDisplay() {
        val d = boardList[currentTabIndex]
        val offset = d.taskTextSize - 14f

        findViewById<TextView>(R.id.board_task_name_left)?.apply {
            text = d.taskName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SIZE_TASK + offset)
        }

        findViewById<TextView>(R.id.board_work_type)?.apply {
            text = d.workType
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SIZE_WORK + offset)
        }

        findViewById<TextView>(R.id.board_location_2)?.apply {
            text = d.location2
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SIZE_LOC2 + offset)
        }

        findViewById<TextView>(R.id.board_jv_name)?.apply {
            text = d.jvName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SIZE_JV + offset)
        }

        findViewById<TextView>(R.id.board_remarks)?.apply {
            text = arrayOf("", "作業前", "作業中", "作業後")[d.remarkIndex]
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SIZE_REMARK + offset)
        }

        findViewById<TextView>(R.id.board_location)?.apply {
            text = if (d.location.isNotEmpty() || d.route.isNotEmpty()) "${d.location} / ${d.route}".trim(' ', '/') else ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SIZE_LOC + offset)
        }

        findViewById<TextView>(R.id.board_date)?.apply {
            text = "R" + JapaneseDate.from(LocalDate.now()).format(DateTimeFormatter.ofPattern("y/MM/dd", Locale.JAPAN))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SIZE_DATE + offset)
        }
    }

    private fun setupSpinners() {
        val r = arrayOf("", "あかしあ公園", "山麓公園", "どんぐり公園", "日新公園", "やちだも公園", "さくらんぼ公園", "北円山公園", "北４条かすみ公園", "南7条りんりん公園", "南１０条明星公園", "北４条まどか公園", "円山裏参道公園", "南６条西２２丁目広場", "南１条西１８丁目広場", "南２条みゆき公園", "南１４条あゆみ公園")
        val l = arrayOf("", "北５条線", "北４条線", "北３条線", "北２条線", "北１条線", "北大通線", "南大通線", "西２０丁目線", "西２１丁目線", "西２３丁目線", "西２４丁目線", "西２５丁目線")
        val m = arrayOf("", "作業前", "作業中", "作業後")

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
                    R.id.spinner_location_input -> boardList[currentTabIndex].location = p.getItemAtPosition(pos).toString()
                    R.id.spinner_route_input -> boardList[currentTabIndex].route = p.getItemAtPosition(pos).toString()
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
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        tabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
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
            try {
                p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pre, imageCapture)
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera binding failed", e)
            }
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