package com.example.calculadoraedoia

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
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
        private val wvEquation: WebView = itemView.findViewById(R.id.wvEquation)
        private val tvPvi: TextView = itemView.findViewById(R.id.tvPvi)
        private val wvSolutionPreview: WebView = itemView.findViewById(R.id.wvSolutionPreview)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        init {
            setupWebView(wvEquation, 16)
            setupWebView(wvSolutionPreview, 14)
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun setupWebView(wv: WebView, fontSize: Int) {
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.webViewClient = WebViewClient()
            wv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            wv.loadDataWithBaseURL(
                "https://cdn.jsdelivr.net/",
                getMathJaxHtml(fontSize),
                "text/html",
                "utf-8",
                null
            )
        }

        private fun isDarkMode(): Boolean {
            return (itemView.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        private fun getMathJaxHtml(fontSize: Int): String {
            val isDark = isDarkMode()
            val bgColor = if (isDark) "#1E1E1E" else "#FFFFFF"
            val textColor = if (isDark) "#E0E0E0" else "#000000"

            return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <style> 
                    body { 
                      font-size: ${fontSize}px; 
                      padding: 4px; 
                      margin: 0; 
                      overflow-wrap: break-word;
                      word-wrap: break-word;
                      line-height: 1.4;
                      background: transparent;
                      color: $textColor;
                    } 
                  </style>
                  <script>
                    window.MathJax = { 
                      tex: { 
                        inlineMath: [['\\\\(','\\\\)']], 
                        displayMath: [['\\\\[','\\\\]']] 
                      },
                      startup: {
                        ready: () => {
                          MathJax.startup.defaultReady();
                        }
                      }
                    };
                  </script>
                  <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
                </head>
                <body>
                  <div id="math"></div>
                </body>
                </html>
            """.trimIndent()
        }

        fun bind(history: EquationHistory) {
            // Renderizar ecuación
            setLatexToWebView(wvEquation, "\\[${history.equation}\\]")

            // PVI
            val hasPvi = !history.x0.isNullOrBlank() || !history.y0.isNullOrBlank()
            if (hasPvi) {
                tvPvi.visibility = View.VISIBLE
                tvPvi.text = "PVI: x₀=${history.x0 ?: ""}, y₀=${history.y0 ?: ""}"
            } else {
                tvPvi.visibility = View.GONE
            }

            // Preview de solución renderizada
            val solutionPreview = extractPreview(history.solution)
            setLatexToWebView(wvSolutionPreview, solutionPreview)

            // Fecha
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            tvDate.text = dateFormat.format(Date(history.timestamp))

            // Click handlers
            itemView.setOnClickListener { onItemClick(history) }
            btnShare.setOnClickListener { onShareClick(history) }
            btnDelete.setOnClickListener { onDeleteClick(history) }
        }

        private fun extractPreview(solution: String): String {
            // Extraer las primeras 2 líneas de LaTeX para el preview
            val lines = solution.split("\\n").filter { it.isNotBlank() }
            return if (lines.size > 2) {
                lines.take(2).joinToString("\\n") + "\\n\\[\\text{...}\\]"
            } else {
                solution
            }
        }

        private fun setLatexToWebView(wv: WebView, latex: String) {
            val js = """
                (function(){
                  const tex = ${jsonString(latex)};
                  const el = document.getElementById('math');
                  if (el) {
                    if (window.MathJax && MathJax.typesetClear) MathJax.typesetClear([el]);
                    el.textContent = tex;
                    if (window.MathJax && MathJax.typesetPromise) {
                      MathJax.typesetPromise([el]).catch((err) => console.log(err));
                    }
                  }
                })();
            """.trimIndent()
            wv.post { wv.evaluateJavascript(js, null) }
        }

        private fun jsonString(s: String): String {
            val sb = StringBuilder()
            sb.append('"')
            for (ch in s) {
                when (ch) {
                    '\\\\' -> sb.append("\\\\\\\\")
                    '"' -> sb.append("\\\\\"")
                    '\\n' -> sb.append("\\\\n")
                    '\\r' -> sb.append("\\\\r")
                    '\\t' -> sb.append("\\\\t")
                    else -> sb.append(ch)
                }
            }
            sb.append('"')
            return sb.toString()
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
