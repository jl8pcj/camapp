package com.example.newcamapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.OkHttp3Requestor
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class GalleryActivity : AppCompatActivity() {

    private val dateMap = mutableMapOf<String, MutableList<Uri>>()
    private var currentSelectedDate: String = ""
    private var rootFolderUri: Uri? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout

    // ★画像から抽出した最新の正しいトークンに差し替えました
    private val REFRESH_TOKEN = "X8Ogs3jedLAAAAAAAAAAAdX899MydSQM8e_-5P5Z60lliQR4iJXRxl81YUmkc4oX"
    private val ACCESS_TOKEN = "sl.u.AGezcO_sS3Ac_R8I-kL..." // SDK内部で即リフレッシュされるため、短くてもOK
    private val APP_KEY = "kwps3743vkv4xqp"
    private val APP_SECRET = "znr5vz3i6myjixw"

    private val dropboxClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()

        val config = DbxRequestConfig.newBuilder("new-cam-app")
            .withHttpRequestor(OkHttp3Requestor(okHttpClient))
            .build()

        // 画像 image_286402.png の内容に基づき設定
        // 改行が含まれないよう文字列を直接指定
        val credential = DbxCredential(
            "dummy_access_token", // 初期値を入れておくことで Missing token を回避
            0L,                   // 期限切れとして扱い、即座に refresh_token を使わせる
            REFRESH_TOKEN,
            APP_KEY,
            APP_SECRET
        )

        DbxClientV2(config, credential)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        recyclerView = findViewById(R.id.recyclerView)
        tabLayout = findViewById(R.id.gallery_tab_layout)

        val btnSendToBox = findViewById<Button>(R.id.btnSendToBox)
        val btnDeleteFolder = findViewById<Button>(R.id.btnDeleteFolder)

        recyclerView.layoutManager = GridLayoutManager(this, 3)

        val folderUriString = intent.getStringExtra("folder_uri")
        if (folderUriString != null) {
            rootFolderUri = Uri.parse(folderUriString)
            loadImagesByDate(rootFolderUri!!)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentSelectedDate = tab?.text.toString()
                displayImagesForDate(currentSelectedDate)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnSendToBox?.setOnClickListener {
            uploadCurrentFolderToDropbox()
        }

        btnDeleteFolder?.setOnClickListener {
            confirmDeleteFolder()
        }
    }

    private fun loadImagesByDate(treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val rootDir = DocumentFile.fromTreeUri(this@GalleryActivity, treeUri)
            dateMap.clear()

            val dateFolders = rootDir?.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name } ?: return@launch

            dateFolders.forEach { dateDir ->
                val dateName = dateDir.name ?: "Unknown"
                val images = dateDir.listFiles()
                    .filter { it.type == "image/jpeg" || it.name?.endsWith(".jpg") == true }
                    .map { it.uri }

                if (images.isNotEmpty()) {
                    dateMap[dateName] = images.toMutableList()
                }
            }

            withContext(Dispatchers.Main) {
                tabLayout.removeAllTabs()
                if (dateMap.isEmpty()) {
                    Toast.makeText(this@GalleryActivity, "写真が見つかりません", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                dateMap.keys.forEach { date ->
                    tabLayout.addTab(tabLayout.newTab().setText(date))
                }

                if (tabLayout.tabCount > 0) {
                    currentSelectedDate = dateMap.keys.first()
                    displayImagesForDate(currentSelectedDate)
                }
            }
        }
    }

    private fun displayImagesForDate(date: String) {
        val uris = dateMap[date] ?: return
        val galleryItems = uris.map { GalleryItem.Image(it) }

        recyclerView.adapter = ImageAdapter(galleryItems) { uri ->
            val intent = Intent(this, FullScreenActivity::class.java)
            intent.putExtra("image_uri", uri.toString())
            startActivity(intent)
        }
    }

    private fun uploadCurrentFolderToDropbox() {
        if (currentSelectedDate.isEmpty()) {
            Toast.makeText(this, "転送するフォルダがありません", Toast.LENGTH_SHORT).show()
            return
        }
        val uris = dateMap[currentSelectedDate] ?: return

        Toast.makeText(this, "Dropboxへ $currentSelectedDate フォルダを転送中...", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                uris.forEach { uri ->
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val fileName = getFileName(uri)
                        val remotePath = "/現場写真/$currentSelectedDate/$fileName"

                        dropboxClient.files().uploadBuilder(remotePath)
                            .withMode(WriteMode.OVERWRITE)
                            .uploadAndFinish(inputStream)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GalleryActivity, "$currentSelectedDate フォルダの転送に成功しました！", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DropboxUpload", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // エラーメッセージを分かりやすく表示
                    val errorMsg = e.localizedMessage ?: "不明なエラー"
                    Toast.makeText(this@GalleryActivity, "転送失敗: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDeleteFolder() {
        if (currentSelectedDate.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("フォルダの削除")
            .setMessage("日付「$currentSelectedDate」のフォルダと中の写真をすべて削除しますか？\n(Dropboxの写真は消えません)")
            .setPositiveButton("削除") { _, _ ->
                deleteCurrentDateFolder()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun deleteCurrentDateFolder() {
        val uri = rootFolderUri ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val rootDir = DocumentFile.fromTreeUri(this@GalleryActivity, uri)
            val folderToDelete = rootDir?.findFile(currentSelectedDate)

            if (folderToDelete != null && folderToDelete.isDirectory) {
                val success = folderToDelete.delete()
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@GalleryActivity, "削除しました", Toast.LENGTH_SHORT).show()
                        loadImagesByDate(uri)
                    } else {
                        Toast.makeText(this@GalleryActivity, "削除に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        return DocumentFile.fromSingleUri(this, uri)?.name ?: "IMG_${System.currentTimeMillis()}.jpg"
    }
}