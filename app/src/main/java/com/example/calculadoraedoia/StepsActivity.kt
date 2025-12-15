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
        // NO escapar el LaTeX, dejarlo como esta
        val texContent = initialTex
            .replace("\\n", "\n")  // Reemplazar \n literales por saltos reales
            .trim()

        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <style>
                * {
                  margin: 0;
                  padding: 0;
                  box-sizing: border-box;
                }
                
                body {
                  font-family: system-ui, -apple-system, sans-serif;
                  padding: 16px;
                  font-size: 18px;
                  line-height: 1.8;
                  background: #FAFAFA;
                  color: #212121;
                }
                
                #math {
                  max-width: 100%;
                  overflow-x: auto;
                  padding-bottom: 24px;
                }
                
                /* Mejorar espaciado entre bloques LaTeX */
                #math > * {
                  margin-bottom: 12px;
                }
                
                /* Estilo para titulos en negrita */
                .MathJax .textbf {
                  font-weight: bold;
                  color: #1976D2;
                }
              </style>
              
              <script>
                window.MathJax = {
                  tex: {
                    inlineMath: [['\\(', '\\)']],
                    displayMath: [['\\[', '\\]']]
                  },
                  chtml: {
                    scale: 1.0,
                    matchFontHeight: false
                  },
                  startup: {
                    ready: () => {
                      MathJax.startup.defaultReady();
                      MathJax.startup.promise.then(() => {
                        console.log('MathJax loaded');
                      });
                    }
                  }
                };
              </script>
              <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            </head>
            <body>
              <div id="math">${texContent}</div>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        const val EXTRA_STEPS_LATEX = "steps_latex"
    }
}
