package com.example.newcamapp
    import android.net.Uri
    import android.os.Bundle
    import android.view.ViewGroup
    import android.widget.ImageView
    import androidx.appcompat.app.AppCompatActivity

    class FullScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // レイアウトファイルを使わず、プログラムで画面いっぱいに画像を表示する設定
    val imageView = ImageView(this).apply {
    layoutParams = ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.MATCH_PARENT
    )
    setBackgroundColor(android.graphics.Color.BLACK)
    scaleType = ImageView.ScaleType.FIT_CENTER
    }
    setContentView(imageView)

    // 前の画面から届いた画像の場所（URI）を受け取って表示
    val uriString = intent.getStringExtra("image_uri")
    if (uriString != null) {
    try {
    imageView.setImageURI(Uri.parse(uriString))
    } catch (e: Exception) {
    android.util.Log.e("FullScreen", "画像の読み込みに失敗しました", e)
    }
    }

    // 画面のどこをタップしても一覧に戻る
    imageView.setOnClickListener { finish() }
    }
    }