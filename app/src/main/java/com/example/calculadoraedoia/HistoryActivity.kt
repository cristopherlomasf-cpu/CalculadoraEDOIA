package com.example.calculadoraedoia

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calculadoraedoia.database.AppDatabase
import com.example.calculadoraedoia.database.EquationHistory
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: HistoryAdapter
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        toolbar = findViewById(R.id.toolbar)

        database = AppDatabase.getDatabase(this)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        setupRecyclerView()
        observeHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { history ->
                returnToMainWithEquation(history)
            },
            onDeleteClick = { history ->
                deleteHistory(history)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            database.equationDao().getAllHistory().collectLatest { historyList ->
                if (historyList.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(historyList)
                }
            }
        }
    }

    private fun returnToMainWithEquation(history: EquationHistory) {
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

    private fun deleteHistory(history: EquationHistory) {
        lifecycleScope.launch {
            database.equationDao().delete(history)
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
