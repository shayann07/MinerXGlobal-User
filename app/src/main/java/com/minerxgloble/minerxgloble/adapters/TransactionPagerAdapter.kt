package com.minerxgloble.minerxgloble.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.models.TransactionModel

class TransactionPagerAdapter(
    private val pages: List<List<TransactionModel>>,
    private val onClick: (TransactionModel) -> Unit
) : RecyclerView.Adapter<TransactionPagerAdapter.PageViewHolder>() {

    inner class PageViewHolder(val recyclerView: RecyclerView) :
        RecyclerView.ViewHolder(recyclerView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val recyclerView = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,  // width = match_parent
                ViewGroup.LayoutParams.MATCH_PARENT   // height = match_parent ← THIS IS CRUCIAL
            )
            layoutManager = LinearLayoutManager(parent.context)
        }
        return PageViewHolder(recyclerView)
    }


    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val adapter = TransactionAdapter(pages[position], onClick)
        holder.recyclerView.adapter = adapter
    }

    override fun getItemCount(): Int = pages.size
}
