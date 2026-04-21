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

    // Dropboxアクセストークン
    private val ACCESS_TOKEN = "sl.u.AGe5ZKAO-y-XAF0f1Bs1J6ZYfOrjyIXSC8jAZ_s8KD62fWHe7rkYNnDAgtCbs02QAyIclrB4c2y1YGJvZBRTBFsjgzk3gUdRL47ML4gUm1Pa0YsMNKJy3iwismPWgj9gTus9ZCdAJqN-_y8VQbJrPVC8OWkD7lBb8xnpp4UJx192zMCqBEVaxrZpqkAHb01tMJI9hkfHXxu8TC7J-m4Wb_IYppYXaccrxFd1ps2JgGIiK2vGj2E2rveuB2zHRebHV1J971UGeVWiKsBdB6iAstIR7CSH8dHnAIT5gIPzl1DQRd4Fd6MlRrfy0RPmNy43-qLqc_eyWKkIPsnaozp7QWe4Rprj7KIoyu29Oy370A78q3eMT2tZNaVZSLRNSuVbt1bnEIzB_v0vAVXuc4b_HzcYC6mlx083gsikybBf_WWELMzEOL0rmBn20mLdmMFS4ppMyY3wbiaNKtC9rhwmrxOOhjrh3HBDvnu0J_b6mmZH5tCEBgnt-bysqEedmToHLbTit-c2gXylMGnumnRo7t08vhJ3Gz6XNL9RstgKOy8NpimJdyJbDdIhdDLIo3hXL5P5uXVlxIlotS3IPp__vvDsuf2TFi80cT9LwjcsOf-8aQC2Y_3fRIzXVaMkqgRoy8mEK3-OLfzxytkHa58cC4N4mxjxv8yWQB1BFmixxlS1ILCOV-H3lDlMDlzhb-L4BcjkRirOdHTgzbtFc1gCiPFp8AueZd6me9-HthukbIBhL_ZaHeO3HM1DiX6TsVFPKLQPDp5WEAYq6Cc_67AtdfOi6AkzjwHn0cefQVKmHU0G2U6yKdekX7l5W4A_aD05_q2benMXjFNattS6pLMcXC1S5GEK9RBIt7uhNs36tLafMmi0WA_0PcqP34SFS34B_Mh6uG4l5PprSU4pCt5BLqJrn4rMZM7Qf0bu3RAOWpJSAKeJRbk_6lF9y20R7JDgeg3u2UDduRZ0fEYiYBWHvGDn4EZmvr_iPS61yzd-lI3hPz979AJ_2IIzs82Pd8NkA_Vh2bu_Tc9jaoOTpGHusvSkTfp0x6oKNoEz830f_COjUrBNAtwLw0fpS9HmiD1PX1bWE29UdUfdZ_P0mOR3Bx4f867hdlpccpWhlVQEBRP1rMXZhWV1zMkW9o38w88Mbokx05x3oeNBcf55sjl_KO_Ngy3mAmoPU5aE0te4xASwekTYCbiMnN7iHBpl9gG9Ra0pxBj3SUgzQNpwlNT-HLumHXAs4Kl3jKUVdcs0CeOdL4SnL0J1KpzoXNOPN-abEcEz5vge3X1oKHZb0tFkyC8s"

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

        // ボタンのIDを明示的に指定して取得
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

        btnSendToBox.setOnClickListener {
            uploadCurrentFolderToDropbox()
        }

        btnDeleteFolder.setOnClickListener {
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
        if (currentSelectedDate.isEmpty()) return
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
                    Toast.makeText(this@GalleryActivity, "転送失敗。接続設定などを確認してください", Toast.LENGTH_LONG).show()
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