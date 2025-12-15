package com.example.calculadoraedoia

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class StepsActivity : AppCompatActivity() {

    private lateinit var wvSteps: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_steps)

        wvSteps = findViewById(R.id.wvSteps)
        val btnClose = findViewById<Button>(R.id.btnCloseSteps)

        wvSteps.settings.javaScriptEnabled = true
        wvSteps.settings.domStorageEnabled = true
        wvSteps.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Cargar el contenido LaTeX después de que la página esté lista
                val stepsLatex = intent.getStringExtra(EXTRA_STEPS_LATEX).orEmpty()
                if (stepsLatex.isNotEmpty()) {
                    setStepsLatex(stepsLatex)
                }
            }
        }

        wvSteps.loadDataWithBaseURL(
            "https://cdn.jsdelivr.net/",
            getBaseMathJaxHtml(),
            "text/html",
            "utf-8",
            null
        )

        btnClose.setOnClickListener { finish() }
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getBaseMathJaxHtml(): String {
        val isDark = isDarkMode()
        val bgColor = if (isDark) "#1E1E1E" else "#FAFAFA"
        val textColor = if (isDark) "#E0E0E0" else "#212121"
        val titleColor = if (isDark) "#64B5F6" else "#1976D2"

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
                  background: $bgColor;
                  color: $textColor;
                }
                
                #math {
                  max-width: 100%;
                  overflow-x: auto;
                  padding-bottom: 24px;
                }
                
                /* Mejorar espaciado entre bloques LaTeX */
                #math .MJXc-display {
                  margin: 16px 0 !important;
                }
                
                /* Estilo para secciones */
                #math > div {
                  margin-bottom: 20px;
                }
              </style>
              
              <script>
                window.MathJax = {
                  tex: {
                    inlineMath: [['\\\\(', '\\\\)']],
                    displayMath: [['\\\\[', '\\\\]']],
                    processEscapes: true,
                    processEnvironments: true
                  },
                  chtml: {
                    scale: 1.1,
                    matchFontHeight: false
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

    private fun setStepsLatex(latex: String) {
        val js = """
            (function(){
              const tex = ${jsonString(latex)};
              const el = document.getElementById('math');
              if (el) {
                if (window.MathJax && MathJax.typesetClear) {
                  MathJax.typesetClear([el]);
                }
                el.textContent = tex;
                if (window.MathJax && MathJax.typesetPromise) {
                  MathJax.typesetPromise([el]).catch((err) => console.log('MathJax error:', err));
                }
              }
            })();
        """.trimIndent()
        wvSteps.evaluateJavascript(js, null)
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    companion object {
        const val EXTRA_STEPS_LATEX = "steps_latex"
    }
}
