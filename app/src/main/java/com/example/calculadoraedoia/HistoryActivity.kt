package com.example.calculadoraedoia

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calculadoraedoia.database.AppDatabase
import com.example.calculadoraedoia.database.EquationHistory
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: HistoryAdapter
    
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerHistory)
        emptyView = findViewById(R.id.emptyView)
        tvEmpty = findViewById(R.id.tvEmpty)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        loadHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { history ->
                loadEquation(history)
            },
            onShareClick = { history ->
                shareEquation(history)
            },
            onDeleteClick = { history ->
                showDeleteDialog(history)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            database.equationDao().getAllHistory().collect { historyList ->
                if (historyList.isEmpty()) {
                    showEmptyState()
                } else {
                    showHistory(historyList)
                }
            }
        }
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
    }

    private fun showHistory(historyList: List<EquationHistory>) {
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        adapter.submitList(historyList)
    }

    private fun loadEquation(history: EquationHistory) {
        val intent = Intent().apply {
            putExtra(EXTRA_EQUATION, history.equation)
            putExtra(EXTRA_X0, history.x0 ?: "")
            putExtra(EXTRA_Y0, history.y0 ?: "")
            putExtra(EXTRA_SOLUTION, history.solution)
            putExtra(EXTRA_STEPS, history.steps)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun shareEquation(history: EquationHistory) {
        val shareText = buildString {
            appendLine("=== Calculadora EDO ===")
            appendLine()
            appendLine("Ecuacion: ${history.equation}")
            if (!history.x0.isNullOrBlank() || !history.y0.isNullOrBlank()) {
                appendLine("PVI: x0=${history.x0}, y0=${history.y0}")
            }
            appendLine()
            appendLine("Solucion:")
            appendLine(history.solution)
            if (history.steps.isNotBlank()) {
                appendLine()
                appendLine("Pasos:")
                appendLine(history.steps)
            }
            appendLine()
            appendLine("Generado con Calculadora EDO")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Solucion EDO")
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir"))
    }

    private fun showDeleteDialog(history: EquationHistory) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Eliminar esta ecuacion del historial?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteEquation(history)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteEquation(history: EquationHistory) {
        lifecycleScope.launch {
            database.equationDao().delete(history)
            Toast.makeText(this@HistoryActivity, "Eliminado", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_EQUATION = "equation"
        const val EXTRA_X0 = "x0"
        const val EXTRA_Y0 = "y0"
        const val EXTRA_SOLUTION = "solution"
        const val EXTRA_STEPS = "steps"
    }
}
