package com.example.newcamapp

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

// 表示するアイテムの型（見出し or 写真）
sealed class GalleryItem {
    data class Header(val date: String) : GalleryItem()
    data class Image(val uri: Uri) : GalleryItem()
}

class ImageAdapter(
    private val items: List<GalleryItem>,
    private val onClick: (Uri) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_IMAGE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is GalleryItem.Header -> TYPE_HEADER
            is GalleryItem.Image -> TYPE_IMAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            // 見出しの作成
            val view = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = 18f
                setPadding(32, 24, 8, 8)
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#333333"))
            }
            HeaderViewHolder(view)
        } else {
            // 写真枠の作成
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    parent.width / 3,
                    parent.width / 3
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(2, 2, 2, 2)
            }
            ImageViewHolder(imageView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is GalleryItem.Header) {
            holder.textView.text = item.date
        } else if (holder is ImageViewHolder && item is GalleryItem.Image) {
            holder.imageView.setImageURI(item.uri)
            holder.imageView.setOnClickListener { onClick(item.uri) }
        }
    }

    override fun getItemCount() = items.size

    class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}