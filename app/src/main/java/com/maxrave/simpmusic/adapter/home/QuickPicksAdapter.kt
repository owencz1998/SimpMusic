package com.maxrave.simpmusic.adapter.home

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.maxrave.simpmusic.data.model.home.Content
import com.maxrave.simpmusic.databinding.ItemQuickPicksBinding

class QuickPicksAdapter(val contentList: ArrayList<Content>, val context: Context, val navController: NavController): RecyclerView.Adapter<QuickPicksAdapter.ViewHolder>() {
    inner class ViewHolder(val binding: ItemQuickPicksBinding): RecyclerView.ViewHolder(binding.root) {

    }
    fun updateData(newData: ArrayList<Content>) {
        contentList.clear()
        contentList.addAll(newData)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemQuickPicksBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int {
        return contentList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val content = contentList[position]
        with(holder){
            binding.tvSongTitle.text = content.title
            var artistName = ""
            if (content.artists != null) {
                for (artist in content.artists) {
                    artistName += artist.name + ", "
                }
            }
            artistName = removeTrailingComma(artistName)
            artistName = removeComma(artistName)
            binding.tvSongArtist.text = artistName
            binding.ivThumbnail.load(content.thumbnails.last().url)
        }
    }
    private fun removeTrailingComma(sentence: String): String {
        val trimmed = sentence.trimEnd()
        return if (trimmed.endsWith(", ")) {
            trimmed.dropLast(2)
        } else {
            trimmed
        }
    }


    private fun removeComma(string: String): String {
        return if (string.endsWith(',')) {
            string.substring(0, string.length - 1)
        } else {
            string
        }
    }
}