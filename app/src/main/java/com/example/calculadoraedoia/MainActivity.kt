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
    <math-field id="mathfield"></math-field>
    
    <script type="module">
        import 'https://unpkg.com/mathlive@0.98.6/dist/mathlive.min.js';
        
        const mf = document.getElementById('mathfield');
        
        // Configuración del teclado personalizado
        mf.setOptions({
            virtualKeyboardMode: 'manual',
            keypressSound: null,
            plonkSound: null,
            
            // Definir capa personalizada
            customVirtualKeyboardLayers: {
                'edo': {
                    styles: '',
                    rows: [
                        [
                            { class: 'keycap', latex: "y'" },
                            { class: 'keycap', latex: "y''" },
                            { class: 'keycap', latex: 'x' },
                            { class: 'keycap', latex: 'y' },
                            { class: 'keycap', latex: '(' },
                            { class: 'keycap', latex: ')' },
                        ],
                        [
                            { class: 'keycap', latex: '7' },
                            { class: 'keycap', latex: '8' },
                            { class: 'keycap', latex: '9' },
                            { class: 'keycap', latex: '+' },
                            { class: 'keycap', latex: '-' },
                            { class: 'action', label: '⌫', command: ['performWithFeedback', 'deleteBackward'] },
                        ],
                        [
                            { class: 'keycap', latex: '4' },
                            { class: 'keycap', latex: '5' },
                            { class: 'keycap', latex: '6' },
                            { class: 'keycap', latex: '\\\\times' },
                            { class: 'keycap', latex: '\\\\div' },
                            { class: 'action', label: 'AC', command: ['performWithFeedback', 'deleteAll'] },
                        ],
                        [
                            { class: 'keycap', latex: '1' },
                            { class: 'keycap', latex: '2' },
                            { class: 'keycap', latex: '3' },
                            { class: 'keycap', latex: '^{#?}' },
                            { class: 'keycap', latex: 'e^{#?}' },
                            { class: 'keycap', latex: '=' },
                        ],
                        [
                            { class: 'keycap', latex: '0' },
                            { class: 'keycap', latex: '.' },
                            { class: 'keycap', latex: '\\\\ln' },
                            { class: 'keycap', latex: '\\\\sin' },
                            { class: 'keycap', latex: '\\\\cos' },
                            { class: 'keycap', latex: '\\\\frac{#?}{#?}' },
                        ],
                    ]
                }
            },
            
            // Usar SOLO nuestro teclado personalizado
            virtualKeyboards: 'edo'
        });
        
        // Mostrar el teclado automáticamente
        setTimeout(() => {
            mf.executeCommand('showVirtualKeyboard');
        }, 500);
        
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

        // PROMPT MEJORADO CON ÉNFASIS EN PRECISIÓN
        val prompt = buildString {
            appendLine("Resuelve esta EDO PASO A PASO con PRECISIÓN MATEMÁTICA. Devuelve solo LaTeX.")
            appendLine("")
            appendLine("Formato requerido:")
            appendLine("")
            appendLine("ANTES de <<<PASOS>>>:")
            appendLine("\\[\\textbf{SOLUCION}\\]")
            appendLine("\\[\\textbf{TIPO:} \\text{tipo de EDO}\\]")
            appendLine("\\[y = \\text{solución completa con constante C si aplica}\\]")
            appendLine("")
            appendLine("<<<PASOS>>>")
            appendLine("")
            appendLine("DESPUÉS de <<<PASOS>>>:")
            appendLine("\\[\\textbf{PASOS DETALLADOS}\\]")
            appendLine("\\[\\textbf{METODO:} \\text{método utilizado}\\]")
            appendLine("Incluye todos los pasos algebraicos intermedios")
            appendLine("")
            appendLine("IMPORTANTE: Verifica cada cálculo y NO inventes soluciones triviales como y=0")
            appendLine("")
            appendLine("EDO: $equation")
            if (hasPvi) appendLine("Condiciones iniciales: x0=$x0, y0=$y0")
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.chat(
                        PplxRequest(
                            model = "sonar-pro",  // BALANCE: Más preciso que sonar, más rápido que sonar-reasoning
                            messages = listOf(
                                PplxMessage("system", "Eres un experto matemático especializado en ecuaciones diferenciales. Resuelve con precisión. Solo LaTeX, sin markdown."),
                                PplxMessage("user", prompt)
                            ),
                            temperature = 0.2  // Reducido para mayor precisión
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
