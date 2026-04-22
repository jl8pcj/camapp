package com.example.newcamapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dropbox.core.v2.files.WriteMode
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ImageAdapter
    private val imageList = mutableListOf<File>()

    // ★ACCESS_TOKEN の定義を削除しました（DropboxClientFactory 内で管理するため）

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        loadImages()

        adapter = ImageAdapter(imageList) { file ->
            val intent = Intent(this, FullScreenActivity::class.java)
            intent.putExtra("image_path", file.absolutePath)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // 「Dropboxへ送信」ボタン
        findViewById<Button>(R.id.btnSendToBox).setOnClickListener {
            if (imageList.isNotEmpty()) {
                uploadAllImagesToDropbox()
            } else {
                Toast.makeText(this, "送信する画像がありません", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadImages() {
        val directory = getExternalFilesDir(null)
        val files = directory?.listFiles { file ->
            file.extension.lowercase() == "jpg" || file.extension.lowercase() == "png"
        }
        files?.let {
            imageList.clear()
            imageList.addAll(it.sortedByDescending { f -> f.lastModified() })
        }
    }

    private fun uploadAllImagesToDropbox() {
        Toast.makeText(this, "アップロードを開始します...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                // ★修正箇所: DropboxClientFactory を使用するように変更
                val client = DropboxClientFactory.getClient()

                for (file in imageList) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dateFolder = sdf.format(Date(file.lastModified()))
                    val dropboxPath = "/$dateFolder/${file.name}"

                    FileInputStream(file).use { inputStream ->
                        client.files().uploadBuilder(dropboxPath)
                            .withMode(WriteMode.OVERWRITE)
                            .uploadAndFinish(inputStream)
                    }
                }

                runOnUiThread {
                    Toast.makeText(this, "すべての画像を送信しました", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "エラー: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}