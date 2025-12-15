package com.example.calculadoraedoia

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class StepsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_steps)

        val wv = findViewById<WebView>(R.id.wvSteps)
        val btnClose = findViewById<Button>(R.id.btnCloseSteps)

        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()

        val stepsLatex = intent.getStringExtra(EXTRA_STEPS_LATEX).orEmpty()
        wv.loadDataWithBaseURL(
            "https://cdn.jsdelivr.net/",
            baseMathJaxHtml(stepsLatex),
            "text/html",
            "utf-8",
            null
        )

        btnClose.setOnClickListener { finish() }
    }

    private fun baseMathJaxHtml(initialTex: String): String {
        // Escape mínimo para meterlo dentro del HTML
        val safe = initialTex
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>

              <style>
                html, body {
                  margin: 0;
                  padding: 0;
                  width: 100%;
                  max-width: 100%;
                  overflow-x: hidden;
                  font-size: 18px;
                }

                /* Forzar que cualquier texto largo se parta hacia abajo */
                #math, #math * {
                  max-width: 100%;
                  box-sizing: border-box;
                  overflow-wrap: break-word;   /* estándar moderno */ 
                  word-wrap: break-word;       /* alias común */
                  word-break: break-word;
                }

                body { padding: 12px; }
                #math { padding-bottom: 24px; }
              </style>

              <script>
                window.MathJax = {
                  tex: { inlineMath: [['\\\(','\\\)']], displayMath: [['\\\[','\\\]']] },
                  chtml: { scale: 1.0 }
                };
              </script>
              <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            </head>
            <body>
              <div id="math">$safe</div>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        const val EXTRA_STEPS_LATEX = "steps_latex"
    }
}
