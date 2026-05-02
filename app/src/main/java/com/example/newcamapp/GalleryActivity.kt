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
import com.dropbox.core.v2.files.WriteMode
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class GalleryActivity : AppCompatActivity() {

    private val allImageUris = mutableListOf<Uri>()
    private lateinit var adapter: ImageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout

    private var currentFolderUri: Uri? = null // 現在表示中の日付フォルダのURI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val btnSendToBox = findViewById<Button>(R.id.btnSendToBox)
        val btnDeleteFolder = findViewById<Button>(R.id.btnDeleteFolder)
        recyclerView = findViewById(R.id.recyclerView)
        tabLayout = findViewById(R.id.gallery_tab_layout)

        // Dropbox送信ボタン
        btnSendToBox.setOnClickListener {
            uploadAllImagesToDropboxAPI()
        }

        // フォルダ削除ボタンの実装
        btnDeleteFolder.setOnClickListener {
            confirmAndDeleteFolder()
        }

        // RecyclerViewの設定
        val layoutManager = GridLayoutManager(this, 3)
        recyclerView.layoutManager = layoutManager

        val folderUriString = intent.getStringExtra("folder_uri")
        if (folderUriString != null) {
            setupGallery(Uri.parse(folderUriString))
        } else {
            Toast.makeText(this, "保存先フォルダが見つかりません", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * タブの設定と初期データの読み込み
     */
    private fun setupGallery(treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rootDir = DocumentFile.fromTreeUri(this@GalleryActivity, treeUri)
                val dateFolders = rootDir?.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name } ?: emptyList()

                withContext(Dispatchers.Main) {
                    tabLayout.removeAllTabs()
                    tabLayout.clearOnTabSelectedListeners()

                    if (dateFolders.isEmpty()) {
                        Toast.makeText(this@GalleryActivity, "写真がありません", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    // リスナーを先に追加
                    tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                        override fun onTabSelected(tab: TabLayout.Tab?) {
                            val uri = tab?.tag as? Uri
                            if (uri != null) {
                                currentFolderUri = uri
                                loadImagesFromFolder(uri)
                            }
                        }
                        override fun onTabUnselected(tab: TabLayout.Tab?) {}
                        override fun onTabReselected(tab: TabLayout.Tab?) {
                            val uri = tab?.tag as? Uri
                            if (uri != null) loadImagesFromFolder(uri)
                        }
                    })

                    dateFolders.forEach { folder ->
                        val tab = tabLayout.newTab().setText(folder.name).setTag(folder.uri)
                        tabLayout.addTab(tab)
                    }

                    // 最初のタブを明示的に選択して読み込みを開始
                    if (tabLayout.tabCount > 0) {
                        val firstTab = tabLayout.getTabAt(0)
                        firstTab?.select()
                        // 既にセレクト状態の場合リスナーが走らないことがあるため直接呼ぶ
                        (firstTab?.tag as? Uri)?.let {
                            currentFolderUri = it
                            loadImagesFromFolder(it)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryActivity", "Error setting up gallery", e)
            }
        }
    }

    /**
     * 指定された日付フォルダから画像を読み込む
     */
    private fun loadImagesFromFolder(folderUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val galleryItems = mutableListOf<GalleryItem>()
                // ★修正: fromSingleUri ではなく fromTreeUri でフォルダを正しく開く
                // または DocumentFile オブジェクトを直接使う
                val dateDir = DocumentFile.fromTreeUri(this@GalleryActivity, folderUri)

                allImageUris.clear()

                val files = dateDir?.listFiles() ?: emptyArray()
                val images = files.filter { it.isFile && it.name?.lowercase()?.endsWith(".jpg") == true }
                    .sortedByDescending { it.lastModified() }

                images.forEach {
                    galleryItems.add(GalleryItem.Image(it.uri))
                    allImageUris.add(it.uri)
                }

                withContext(Dispatchers.Main) {
                    Log.d("GalleryActivity", "Found ${galleryItems.size} images in $folderUri")

                    adapter = ImageAdapter(galleryItems) { selectedUri ->
                        val intent = Intent(this@GalleryActivity, FullScreenActivity::class.java)
                        intent.putExtra("image_uri", selectedUri.toString())
                        startActivity(intent)
                    }
                    recyclerView.adapter = adapter

                    if (galleryItems.isEmpty()) {
                        Toast.makeText(this@GalleryActivity, "このフォルダに画像はありません", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryActivity", "Error loading images from $folderUri", e)
            }
        }
    }

    /**
     * 削除確認ダイアログを表示し、実行されたらフォルダを消す
     */
    private fun confirmAndDeleteFolder() {
        val folderUri = currentFolderUri ?: return
        val dateName = tabLayout.getTabAt(tabLayout.selectedTabPosition)?.text ?: "このフォルダ"

        AlertDialog.Builder(this)
            .setTitle("フォルダの削除")
            .setMessage("${dateName} の写真とフォルダをすべて削除しますか？\n（この操作は取り消せません）")
            .setPositiveButton("削除する") { _, _ ->
                deleteFolderProcess(folderUri)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun deleteFolderProcess(folderUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // フォルダの削除も fromTreeUri で取得した DocumentFile で行う
                val folderFile = DocumentFile.fromTreeUri(this@GalleryActivity, folderUri)
                if (folderFile?.delete() == true) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@GalleryActivity, "削除しました", Toast.LENGTH_SHORT).show()
                        val rootUriString = intent.getStringExtra("folder_uri")
                        if (rootUriString != null) {
                            setupGallery(Uri.parse(rootUriString))
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@GalleryActivity, "削除できませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GalleryActivity, "削除失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Dropbox APIを使用して全ての写真をアップロード
     */
    private fun uploadAllImagesToDropboxAPI() {
        if (allImageUris.isEmpty()) {
            Toast.makeText(this, "送信する写真がありません", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Dropboxへ送信中...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = DropboxClientFactory.getClient(this@GalleryActivity)
                for (uri in allImageUris) {
                    // ここも fromSingleUri だとストリームが開けない場合があるため注意
                    val file = DocumentFile.fromSingleUri(this@GalleryActivity, uri) ?: continue
                    val fileName = file.name ?: "IMG_${System.currentTimeMillis()}.jpg"

                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dateFolderName = sdf.format(Date(file.lastModified()))
                    val dropboxPath = "/現場写真/$dateFolderName/$fileName"

                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        client.files().uploadBuilder(dropboxPath)
                            .withMode(WriteMode.OVERWRITE)
                            .uploadAndFinish(inputStream)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GalleryActivity, "送信完了しました", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GalleryActivity, "送信エラー: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}