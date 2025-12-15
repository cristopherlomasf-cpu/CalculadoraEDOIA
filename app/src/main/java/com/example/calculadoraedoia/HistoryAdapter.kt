package com.example.calculadoraedoia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calculadoraedoia.database.EquationHistory
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onItemClick: (EquationHistory) -> Unit,
    private val onShareClick: (EquationHistory) -> Unit,
    private val onDeleteClick: (EquationHistory) -> Unit
) : ListAdapter<EquationHistory, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEquation: TextView = itemView.findViewById(R.id.tvEquation)
        private val tvPvi: TextView = itemView.findViewById(R.id.tvPvi)
        private val tvSolutionPreview: TextView = itemView.findViewById(R.id.tvSolutionPreview)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(history: EquationHistory) {
            // Ecuacion
            tvEquation.text = history.equation

            // PVI
            val hasPvi = !history.x0.isNullOrBlank() || !history.y0.isNullOrBlank()
            if (hasPvi) {
                tvPvi.visibility = View.VISIBLE
                tvPvi.text = "PVI: x0=${history.x0 ?: ""}, y0=${history.y0 ?: ""}"
            } else {
                tvPvi.visibility = View.GONE
            }

            // Preview de solucion (primeras 80 caracteres)
            val preview = history.solution.take(80)
            tvSolutionPreview.text = if (history.solution.length > 80) {
                "$preview..."
            } else {
                preview
            }

            // Fecha
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            tvDate.text = dateFormat.format(Date(history.timestamp))

            // Click handlers
            itemView.setOnClickListener { onItemClick(history) }
            btnShare.setOnClickListener { onShareClick(history) }
            btnDelete.setOnClickListener { onDeleteClick(history) }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<EquationHistory>() {
        override fun areItemsTheSame(oldItem: EquationHistory, newItem: EquationHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: EquationHistory, newItem: EquationHistory): Boolean {
            return oldItem == newItem
        }
    }
}
