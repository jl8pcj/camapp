package com.example.newcamapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryActivity : AppCompatActivity() {

    // 送信対象のURIをすべて貯めるリスト
    private val allImageUris = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val btnSendToBox = findViewById<Button>(R.id.btnSendToBox)
        btnSendToBox.setOnClickListener {
            // Dropboxなどへの送信処理（ の同期機能と連携）
            sendAllPhotosToDropbox()
        }

        val folderUriString = intent.getStringExtra("folder_uri")
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        val layoutManager = GridLayoutManager(this, 3)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (recyclerView.adapter?.getItemViewType(position) == 0) 3 else 1
            }
        }
        recyclerView.layoutManager = layoutManager

        if (folderUriString != null) {
            loadImagesWithSections(Uri.parse(folderUriString), recyclerView)
        }
    }

    private fun loadImagesWithSections(treeUri: Uri, recyclerView: RecyclerView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val galleryItems = mutableListOf<GalleryItem>()
            val rootDir = DocumentFile.fromTreeUri(this@GalleryActivity, treeUri)
            allImageUris.clear() // リストを初期化

            val dateFolders = rootDir?.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }

            dateFolders?.forEach { dateDir ->
                val images = dateDir.listFiles()
                    .filter { it.type == "image/jpeg" || it.name?.endsWith(".jpg") == true }
                    .sortedByDescending { it.lastModified() }

                if (images.isNotEmpty()) {
                    galleryItems.add(GalleryItem.Header(dateDir.name ?: ""))
                    images.forEach {
                        galleryItems.add(GalleryItem.Image(it.uri))
                        // 全写真を送信リストに追加
                        allImageUris.add(it.uri)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                recyclerView.adapter = ImageAdapter(galleryItems) { uri ->
                    val intent = Intent(this@GalleryActivity, FullScreenActivity::class.java)
                    intent.putExtra("image_uri", uri.toString())
                    startActivity(intent)
                }
            }
        }
    }

    private fun sendAllPhotosToDropbox() {
        if (allImageUris.isEmpty()) {
            Toast.makeText(this, "送信する写真がありません", Toast.LENGTH_SHORT).show()
            return
        }

        // 外部共有メニューを開く（Dropboxがインストールされていれば選択可能）
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(allImageUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "BOXへ送信"))
    }
}