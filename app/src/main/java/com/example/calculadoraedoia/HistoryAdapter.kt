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
    private val onDeleteClick: (EquationHistory) -> Unit
) : ListAdapter<EquationHistory, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {

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
        private val tvSolution: TextView = itemView.findViewById(R.id.tvSolution)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(history: EquationHistory) {
            // Display equation
            val eqDisplay = if (history.x0 != null || history.y0 != null) {
                "${history.equation} | PVI: x0=${history.x0}, y0=${history.y0}"
            } else {
                history.equation
            }
            tvEquation.text = eqDisplay

            // Extract solution preview
            val solutionPreview = extractSolutionPreview(history.solution)
            tvSolution.text = solutionPreview

            // Format timestamp
            tvTimestamp.text = formatTimestamp(history.timestamp)

            // Click handlers
            itemView.setOnClickListener {
                onItemClick(history)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(history)
            }
        }

        private fun extractSolutionPreview(solution: String): String {
            // Extract first line or y = ...
            val lines = solution.split("\n")
            for (line in lines) {
                if (line.contains("y =") || line.contains("y=")) {
                    return line.replace("\\[", "").replace("\\]", "").take(80)
                }
            }
            return "Ver solucion completa"
        }

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60_000 -> "Hace un momento"
                diff < 3600_000 -> "Hace ${diff / 60_000} minutos"
                diff < 86400_000 -> "Hace ${diff / 3600_000} horas"
                diff < 604800_000 -> "Hace ${diff / 86400_000} dias"
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<EquationHistory>() {
        override fun areItemsTheSame(oldItem: EquationHistory, newItem: EquationHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: EquationHistory, newItem: EquationHistory): Boolean {
            return oldItem == newItem
        }
    }
}
