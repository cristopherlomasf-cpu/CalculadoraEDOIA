package com.example.calculadoraedoia

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val api by lazy { PerplexityClient.create(BuildConfig.PPLX_API_KEY) }

    private lateinit var etEquation: EditText
    private lateinit var etX0: EditText
    private lateinit var etY0: EditText

    private lateinit var tvFieldLabel: TextView
    private lateinit var wvMathLive: WebView
    private lateinit var wvResult: WebView

    private lateinit var btnPvi: Button
    private lateinit var btnSolve: Button
    private lateinit var btnToggleSteps: Button

    // 0=EDO, 1=x0, 2=y0
    private var activeField = 0

    // Toggle pasos
    private var lastSolutionLatex: String? = null
    private var lastStepsLatex: String? = null

    private var isMathLiveReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etEquation = findViewById(R.id.etEquation)
        etX0 = findViewById(R.id.etX0)
        etY0 = findViewById(R.id.etY0)

        tvFieldLabel = findViewById(R.id.tvFieldLabel)
        wvMathLive = findViewById(R.id.wvMathLive)
        wvResult = findViewById(R.id.wvLatexResult)

        btnPvi = findViewById(R.id.btnPvi)
        btnSolve = findViewById(R.id.btnSolve)
        btnToggleSteps = findViewById(R.id.btnToggleSteps)
        btnToggleSteps.text = "Pasos"

        setupMathLiveEditor()
        setupWebView(wvResult)

        btnPvi.setOnClickListener {
            activeField = when (activeField) {
                0 -> 1
                1 -> 2
                else -> 0
            }
            updateFieldLabel()
            updatePviButtonLabel()
            focusMathLive()
        }

        btnSolve.setOnClickListener { onSolveClicked() }

        btnToggleSteps.setOnClickListener {
            val steps = lastStepsLatex
            if (steps.isNullOrBlank()) {
                setResultLatex("\\[\\text{No hay pasos disponibles.}\\]")
                return@setOnClickListener
            }
            val i = android.content.Intent(this, StepsActivity::class.java)
            i.putExtra(StepsActivity.EXTRA_STEPS_LATEX, steps)
            startActivity(i)
        }

        // Estado inicial
        updateFieldLabel()
        updatePviButtonLabel()
        btnToggleSteps.isEnabled = false
        setResultLatex("\\[\\text{Presiona Resolver.}\\]")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMathLiveEditor() {
        wvMathLive.settings.javaScriptEnabled = true
        wvMathLive.settings.domStorageEnabled = true
        wvMathLive.addJavascriptInterface(MathLiveInterface(), "Android")
        wvMathLive.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isMathLiveReady = true
                Log.d("MathLive", "Editor ready")
            }
        }
        wvMathLive.loadDataWithBaseURL(
            "https://unpkg.com/",
            getMathLiveHtml(),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun getMathLiveHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: system-ui;
            padding: 12px;
            background: #FAFAFA;
            overflow: hidden;
            height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        #mathfield {
            font-size: 32px;
            padding: 16px;
            border: 2px solid #E0E0E0;
            border-radius: 8px;
            background: white;
            width: 100%;
            min-height: 80px;
        }
        #mathfield:focus {
            border-color: #1976D2;
            outline: none;
        }
    </style>
</head>
<body>
    <math-field id="mathfield">
    </math-field>
    
    <script type="module">
        import 'https://unpkg.com/mathlive@0.98.6/dist/mathlive.min.js';
        
        const mf = document.getElementById('mathfield');
        
        // TECLADO VIRTUAL PERSONALIZADO PARA EDO
        mf.setOptions({
            virtualKeyboardMode: 'manual',
            keypressSound: null,
            plonkSound: null,
            customVirtualKeyboardLayers: {
                'edo-layer': {
                    styles: '',
                    rows: [
                        [
                            { label: "y'", latex: "y'" },
                            { label: "y''", latex: "y''" },
                            { label: 'dy/dx', latex: '\\\\frac{dy}{dx}' },
                            { label: 'x', latex: 'x' },
                            { label: 'y', latex: 'y' },
                            { label: '(', latex: '(' },
                            { label: ')', latex: ')' },
                        ],
                        [
                            { label: '7', latex: '7' },
                            { label: '8', latex: '8' },
                            { label: '9', latex: '9' },
                            { label: '+', latex: '+' },
                            { label: '−', latex: '-' },
                            { class: 'action', label: '<svg><use xlink:href="#svg-arrow-left" /></svg>', command: ['performWithFeedback', 'deleteBackward'] },
                        ],
                        [
                            { label: '4', latex: '4' },
                            { label: '5', latex: '5' },
                            { label: '6', latex: '6' },
                            { label: '×', latex: '\\\\times' },
                            { label: '÷', latex: '\\\\div' },
                            { class: 'action', label: 'CLR', command: ['performWithFeedback', 'deleteAll'] },
                        ],
                        [
                            { label: '1', latex: '1' },
                            { label: '2', latex: '2' },
                            { label: '3', latex: '3' },
                            { label: 'x^y', latex: '^' },
                            { label: 'e^x', latex: 'e^{#?}' },
                            { label: '=', latex: '=' },
                        ],
                        [
                            { label: '0', latex: '0' },
                            { label: '.', latex: '.' },
                            { label: 'ln', latex: '\\\\ln' },
                            { label: 'sin', latex: '\\\\sin' },
                            { label: 'cos', latex: '\\\\cos' },
                            { label: 'a/b', latex: '\\\\frac{#?}{#?}' },
                        ],
                    ]
                }
            },
            customVirtualKeyboards: {
                'edo-keyboard': {
                    label: 'EDO',
                    tooltip: 'Teclado para EDO',
                    layer: 'edo-layer'
                }
            },
            virtualKeyboards: 'edo-keyboard'
        });
        
        // Mostrar teclado automáticamente
        mf.executeCommand('showVirtualKeyboard');
        
        // Notificar a Android cuando cambia el contenido
        mf.addEventListener('input', () => {
            const latex = mf.value;
            if (window.Android) {
                Android.onLatexChanged(latex);
            }
        });
        
        // Exponer funciones para Android
        window.setLatex = function(latex) {
            mf.value = latex;
        };
        
        window.getLatex = function() {
            return mf.value;
        };
        
        window.insertLatex = function(latex) {
            mf.executeCommand(['insert', latex]);
        };
        
        window.clearMathfield = function() {
            mf.value = '';
        };
        
        window.deleteBackward = function() {
            mf.executeCommand('deleteBackward');
        };
        
        // Auto-focus
        setTimeout(() => mf.focus(), 300);
    </script>
</body>
</html>
        """.trimIndent()
    }

    inner class MathLiveInterface {
        @JavascriptInterface
        fun onLatexChanged(latex: String) {
            runOnUiThread {
                when (activeField) {
                    0 -> etEquation.setText(latex)
                    1 -> etX0.setText(latex)
                    2 -> etY0.setText(latex)
                }
                Log.d("MathLive", "LaTeX updated: $latex")
            }
        }
    }

    private fun updateFieldLabel() {
        when (activeField) {
            0 -> {
                tvFieldLabel.text = "EDO:"
                tvFieldLabel.setTextColor(Color.parseColor("#1976D2"))
            }
            1 -> {
                tvFieldLabel.text = "Condición inicial x₀:"
                tvFieldLabel.setTextColor(Color.parseColor("#388E3C"))
            }
            2 -> {
                tvFieldLabel.text = "Condición inicial y₀:"
                tvFieldLabel.setTextColor(Color.parseColor("#D32F2F"))
            }
        }
    }

    private fun updatePviButtonLabel() {
        btnPvi.text = when (activeField) {
            1 -> "x₀"
            2 -> "y₀"
            else -> "PVI"
        }
    }

    private fun focusMathLive() {
        if (!isMathLiveReady) return
        
        // Cargar el valor del campo actual en MathLive
        val currentValue = when (activeField) {
            1 -> etX0.text.toString()
            2 -> etY0.text.toString()
            else -> etEquation.text.toString()
        }
        
        val jsCmd = "setLatex(${jsonString(currentValue)});"
        wvMathLive.evaluateJavascript(jsCmd, null)
    }

    // -------- Resolver --------

    private fun onSolveClicked() {
        val equation = etEquation.text.toString().trim()
        val x0 = etX0.text.toString().trim()
        val y0 = etY0.text.toString().trim()

        if (equation.isBlank()) {
            lastSolutionLatex = null
            lastStepsLatex = null
            btnToggleSteps.isEnabled = false
            setResultLatex("\\[\\text{Error: escribe una EDO.}\\]")
            return
        }

        val hasPvi = x0.isNotBlank() || y0.isNotBlank()

        btnToggleSteps.isEnabled = false
        lastSolutionLatex = null
        lastStepsLatex = null

        setResultLatex("\\[\\text{Resolviendo...}\\]")

        val prompt = buildString {
            appendLine("Resuelve esta EDO y devuelve SOLO LaTeX para MathJax. NO uses bloques de código ni markdown.")
            appendLine("")
            appendLine("FORMATO ESTRICTO:")
            appendLine("")
            appendLine("Parte 1 (ANTES del delimitador):")
            appendLine("\\[\\textbf{SOLUCION}\\]")
            appendLine("\\[\\textbf{TIPO:} \\text{descripción del tipo}\\]")
            appendLine("\\[y = \\text{resultado final}\\]")
            appendLine("")
            appendLine("Delimitador obligatorio en una línea sola:")
            appendLine("<<<PASOS>>>")
            appendLine("")
            appendLine("Parte 2 (DESPUÉS del delimitador):")
            appendLine("\\[\\textbf{PASOS}\\]")
            appendLine("\\[\\textbf{METODO:} \\text{nombre del método}\\]")
            appendLine("\\[\\text{Paso 1: Descripción corta}\\]")
            appendLine("\\[ecuaciones\\]")
            appendLine("\\[\\text{Paso 2: Descripción corta}\\]")
            appendLine("\\[ecuaciones\\]")
            appendLine("(continúa hasta terminar)")
            appendLine("")
            appendLine("REGLAS IMPORTANTES:")
            appendLine("1. NO uses bloques ```latex```")
            appendLine("2. NO uses barras invertidas para espaciar. Usa espacios normales")
            appendLine("3. TODA la solución debe estar ANTES de <<<PASOS>>>")
            appendLine("4. TODOS los pasos deben estar DESPUÉS de <<<PASOS>>>")
            appendLine("5. Cada paso debe tener descripción y ecuación en líneas separadas")
            appendLine("6. Verifica tus cálculos y sé PRECISO en los resultados")
            appendLine("")
            appendLine("EDO: $equation")
            if (hasPvi) appendLine("PVI: x0=$x0, y0=$y0")
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.chat(
                        PplxRequest(
                            model = "sonar-reasoning",
                            messages = listOf(
                                PplxMessage("system", "Eres un experto matemático. Devuelve únicamente LaTeX válido para MathJax. Sin bloques de código ni markdown. Verifica tus cálculos cuidadosamente."),
                                PplxMessage("user", prompt)
                            ),
                            temperature = 0.1
                        )
                    )
                }

                val raw = resp.choices.firstOrNull()?.message?.content ?: ""
                val cleaned = raw
                    .replace(Regex("(?s)<think>.*?</think>\\s*"), "")
                    .replace("```latex", "")
                    .replace("```", "")
                    .trim()

                val (sol, steps) = splitSolutionSteps(cleaned)
                lastSolutionLatex = sol
                lastStepsLatex = steps

                btnToggleSteps.isEnabled = true
                setResultLatex(sol)

            } catch (e: HttpException) {
                btnToggleSteps.isEnabled = false
                setResultLatex("\\[\\text{HTTP ${e.code()}: ${e.message()}}\\]")
                Log.e("NET", "HTTP error", e)
            } catch (e: IOException) {
                btnToggleSteps.isEnabled = false
                setResultLatex("\\[\\text{Error de red (timeout).}\\]")
                Log.e("NET", "Network error", e)
            } catch (e: Exception) {
                btnToggleSteps.isEnabled = false
                setResultLatex("\\[\\text{Error: ${e.javaClass.simpleName}}\\]")
                Log.e("NET", "Unknown error", e)
            }
        }
    }

    private fun splitSolutionSteps(allLatex: String): Pair<String, String> {
        val delim = "<<<PASOS>>>"
        val parts = allLatex.split(delim, limit = 2)
        
        val solution = if (parts.isNotEmpty()) parts[0].trim() else ""
        val steps = if (parts.size > 1) parts[1].trim() else ""

        val safeSolution = solution.ifBlank {
            "\\[\\textbf{SOLUCION}\\]\n\\[\\textbf{TIPO:} \\text{(no disponible)}\\]"
        }
        val safeSteps = steps.ifBlank {
            "\\[\\textbf{PASOS}\\]\n\\[\\textbf{METODO:} \\text{(no disponible)}\\]"
        }
        return Pair(safeSolution, safeSteps)
    }

    // -------- WebView result --------

    private fun setupWebView(wv: WebView) {
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()
        wv.loadDataWithBaseURL("https://cdn.jsdelivr.net/", baseMathJaxHtml(), "text/html", "utf-8", null)
    }

    private fun baseMathJaxHtml(): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <style> 
                body { 
                  font-size: 20px; 
                  padding: 12px; 
                  margin: 0; 
                  overflow-wrap: break-word;
                  word-wrap: break-word;
                  line-height: 1.6;
                } 
              </style>
              <script>
                window.MathJax = { tex: { inlineMath: [['\\(','\\']], displayMath: [['\\[','\\]']] } };
              </script>
              <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            </head>
            <body>
              <div id="math"></div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun setResultLatex(latex: String) {
        val js = """
            (function(){
              const tex = ${jsonString(latex)};
              const el = document.getElementById('math');
              if (window.MathJax && MathJax.typesetClear) MathJax.typesetClear([el]);
              el.textContent = tex;
              if (window.MathJax && MathJax.typesetPromise) MathJax.typesetPromise([el]);
            })();
        """.trimIndent()
        wvResult.evaluateJavascript(js, null)
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
}
