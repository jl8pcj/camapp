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

    // ★【重要】Dropboxアクセストークン
    // 短期トークンは数時間で切れるため、エラーが出る場合は再度生成してください
    private val ACCESS_TOKEN = "sl.u.AGe8rMclH5JfbiiytGKmofLU87MjNSmDu51ZuGgYKZ8raTp6U7VXH3Y6aRDddpn22uGRBbk8MMwIoxY5pCu2GTMm2LKflYqQNJJ-n_7nLakgpV0OOPyjs8B6X2y4mqiDUx6L77yTx7XGGjj-_euizOmzE7GUskrjsV4JFzl6r_euPpAgqr7KSHuPVaFewIuyRFZBU6tOwWuRXbDE42o81VcSf0fQQVKrI2xXTv8B6Enh0QrHq8Ky5YUD0a5rZlhuZ-mjU27ik-jM8eSaiXliPsXRYa7KEtKMcyQUv6vQtLtyzDxq0yzUkazLRMn8m__LDzdVn5IzsgXDnC6YamubQSIrkarYMAc4mlX6H8SoJlhiwpIKW-qf8YUGA2A98-6m15KnLGdQy2NIswfeVqa4Vhhvwt60UhT-klmStmcO1R8aZClBayWRfWX5301h1MHI-ZmEhFae6zAda37AHK2ezN7iaGQgu-Y1TCU1GGtLetsJ31ALo0H50BIJucucSTfdpW7326Y5lfLeNE-yISi66G7Bh_MPRHhDBmXKGajPGieq2eC4G1wzJBxEQyeEMdkg7vvZz2WYI2cUCjnd_-_jWEreG4TDZ4D7eHqjgc4FpRUIzCx9yxxfbIYYdej0rnNArSIbvYvZvUjtnyTzqHjKSVF0ao6Z60PMuvpRLt-6WFgGnoJFEJahFSQJ2D3G-eTwtEb9LHpxLqwXHijWO3duwVJxQnIZaYek8zYEj4X-e1KbNDeyUqDqvua9om8kaEBt94NFDTL7TM_1AE8XV4qulRy5ktAfdhceKISa1sXlf7OGfQ0zITO_DIC9DVROgssfd7H5tij7gSqfHdyXmEWnVilsrQe443RYZBZ-aMArx4_ETJchiIOOl5CKg9QM7E6Pjfoay6RqC7u2g4ytMTJC05u7M9lZGu0CMSnraTEDNzZGKLyeOUf1hbxp9Bml7jcmpFP7dAerZoNUklt4DU-57zw5K-J5cU0D3PflJSfkd0E1R96Texs75njTc7T1KU4E_IccMxI-ZBxRA734GJRkY-bN56ROUG6UwEPACWEBRIfK7SABh62ScxGMa4PWgMHDv3UJdSFqdnFf0PxU6XKjeKS-q0GZaJUMbRMoZ0EOPIqmAkucq-8UjZnPv6vKhEkSelnTvKcWISdLG4ZcOvM2zvz9EIU__BXQTqLQ-u7Zl-mz83fV3gzi9TAA3K_59NoGfWeiE9Tyt4GOixtA1_1-VEcB3rj6mxeLzKiYFzzPmUUK3PW04mQun8DIaIE_HnPnDtjMtR7z-kbsYdAHp-4ugXgP"

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

        DbxClientV2(config, ACCESS_TOKEN)
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

                currentSelectedDate = dateMap.keys.first()
                displayImagesForDate(currentSelectedDate)
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
                    val errorMsg = e.localizedMessage ?: "不明なエラー"
                    if (errorMsg.contains("401")) {
                        Toast.makeText(this@GalleryActivity, "認証エラー: トークンの期限が切れています", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@GalleryActivity, "転送失敗: $errorMsg", Toast.LENGTH_LONG).show()
                    }
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