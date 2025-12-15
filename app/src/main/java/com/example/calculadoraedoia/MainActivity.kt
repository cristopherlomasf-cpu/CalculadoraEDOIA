package com.example.calculadoraedoia

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.calculadoraedoia.database.AppDatabase
import com.example.calculadoraedoia.database.EquationHistory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val api by lazy { PerplexityClient.create(BuildConfig.PPLX_API_KEY) }
    private val database by lazy { AppDatabase.getDatabase(this) }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etEquation: EditText
    private lateinit var etX0: EditText
    private lateinit var etY0: EditText
    private lateinit var tvFieldLabel: TextView
    private lateinit var wvMathLive: WebView
    private lateinit var wvResult: WebView
    private lateinit var btnPvi: Button
    private lateinit var btnSolve: Button
    private lateinit var btnToggleSteps: Button
    private lateinit var fabCamera: FloatingActionButton

    private var activeField = 0
    private var lastSolutionLatex: String? = null
    private var lastStepsLatex: String? = null
    private var isMathLiveReady = false

    private val cameraOcrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val latex = result.data?.getStringExtra(CameraOcrActivity.EXTRA_LATEX_RESULT)
            if (!latex.isNullOrBlank()) {
                insertLatexIntoEditor(latex)
            }
        }
    }

    private val historyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val equation = result.data?.getStringExtra(HistoryActivity.EXTRA_EQUATION) ?: ""
            val x0 = result.data?.getStringExtra(HistoryActivity.EXTRA_X0) ?: ""
            val y0 = result.data?.getStringExtra(HistoryActivity.EXTRA_Y0) ?: ""
            val solution = result.data?.getStringExtra(HistoryActivity.EXTRA_SOLUTION) ?: ""
            val steps = result.data?.getStringExtra(HistoryActivity.EXTRA_STEPS) ?: ""

            loadFromHistory(equation, x0, y0, solution, steps)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        etEquation = findViewById(R.id.etEquation)
        etX0 = findViewById(R.id.etX0)
        etY0 = findViewById(R.id.etY0)
        tvFieldLabel = findViewById(R.id.tvFieldLabel)
        wvMathLive = findViewById(R.id.wvMathLive)
        wvResult = findViewById(R.id.wvLatexResult)
        btnPvi = findViewById(R.id.btnPvi)
        btnSolve = findViewById(R.id.btnSolve)
        btnToggleSteps = findViewById(R.id.btnToggleSteps)
        fabCamera = findViewById(R.id.fabCamera)
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
            val i = Intent(this, StepsActivity::class.java)
            i.putExtra(StepsActivity.EXTRA_STEPS_LATEX, steps)
            startActivity(i)
        }

        fabCamera.setOnClickListener {
            val intent = Intent(this, CameraOcrActivity::class.java)
            cameraOcrLauncher.launch(intent)
        }

        updateFieldLabel()
        updatePviButtonLabel()
        btnToggleSteps.isEnabled = false
        setResultLatex("\\[\\text{Presiona Resolver.}\\]")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history -> {
                openHistory()
                true
            }
            R.id.action_share -> {
                shareSolution()
                true
            }
            R.id.action_dark_mode -> {
                toggleDarkMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openHistory() {
        val intent = Intent(this, HistoryActivity::class.java)
        historyLauncher.launch(intent)
    }

    private fun shareSolution() {
        val equation = etEquation.text.toString()
        val solution = lastSolutionLatex
        val steps = lastStepsLatex

        if (equation.isBlank() || solution == null) {
            Toast.makeText(this, "Resuelve una ecuacion primero", Toast.LENGTH_SHORT).show()
            return
        }

        val shareText = buildString {
            appendLine("=== Calculadora EDO ===")
            appendLine()
            appendLine("Ecuacion: $equation")
            appendLine()
            appendLine("Solucion:")
            appendLine(solution)
            if (!steps.isNullOrBlank()) {
                appendLine()
                appendLine("Pasos:")
                appendLine(steps)
            }
            appendLine()
            appendLine("Generado con Calculadora EDO")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Solucion EDO")
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir solucion"))
    }

    private fun toggleDarkMode() {
        val currentMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val newMode = if (currentMode == Configuration.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(newMode)
        // Recrear activity para aplicar cambios
        recreate()
    }

    private fun loadFromHistory(equation: String, x0: String, y0: String, solution: String, steps: String) {
        etEquation.setText(equation)
        etX0.setText(x0)
        etY0.setText(y0)

        if (isMathLiveReady) {
            val jsCmd = "setLatex(${jsonString(equation)});"
            wvMathLive.evaluateJavascript(jsCmd, null)
        }

        lastSolutionLatex = solution
        lastStepsLatex = steps
        btnToggleSteps.isEnabled = steps.isNotBlank()
        setResultLatex(solution)
    }

    private fun saveToHistory(equation: String, x0: String?, y0: String?, solution: String, steps: String) {
        lifecycleScope.launch {
            try {
                val history = EquationHistory(
                    equation = equation,
                    x0 = x0,
                    y0 = y0,
                    solution = solution,
                    steps = steps
                )
                database.equationDao().insert(history)
                // Mantener solo las ultimas 20
                database.equationDao().deleteOldHistory()
            } catch (e: Exception) {
                Log.e("DB", "Error saving history", e)
            }
        }
    }

    private fun insertLatexIntoEditor(latex: String) {
        if (!isMathLiveReady) return
        when (activeField) {
            0 -> etEquation.setText(latex)
            1 -> etX0.setText(latex)
            2 -> etY0.setText(latex)
        }
        val jsCmd = "setLatex(${jsonString(latex)});"
        wvMathLive.evaluateJavascript(jsCmd, null)
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

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getMathLiveHtml(): String {
        val isDark = isDarkMode()
        val bgColor = if (isDark) "#1E1E1E" else "#FAFAFA"
        val cardBg = if (isDark) "#2C2C2C" else "#FFFFFF"
        val borderColor = if (isDark) "#444444" else "#E0E0E0"
        val textColor = if (isDark) "#E0E0E0" else "#000000"

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
            background: $bgColor;
            overflow: hidden;
            height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        #mathfield {
            font-size: 32px;
            padding: 16px;
            border: 2px solid $borderColor;
            border-radius: 8px;
            background: $cardBg;
            color: $textColor;
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
        mf.setOptions({
            virtualKeyboardMode: 'manual',
            keypressSound: null,
            plonkSound: null,
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
            virtualKeyboards: 'edo'
        });
        setTimeout(() => { mf.executeCommand('showVirtualKeyboard'); }, 500);
        mf.addEventListener('input', () => {
            if (window.Android) Android.onLatexChanged(mf.value);
        });
        window.setLatex = function(latex) { mf.value = latex; };
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
                tvFieldLabel.text = "Condicion inicial x0:"
                tvFieldLabel.setTextColor(Color.parseColor("#388E3C"))
            }
            2 -> {
                tvFieldLabel.text = "Condicion inicial y0:"
                tvFieldLabel.setTextColor(Color.parseColor("#D32F2F"))
            }
        }
    }

    private fun updatePviButtonLabel() {
        btnPvi.text = when (activeField) {
            1 -> "x0"
            2 -> "y0"
            else -> "PVI"
        }
    }

    private fun focusMathLive() {
        if (!isMathLiveReady) return
        val currentValue = when (activeField) {
            1 -> etX0.text.toString()
            2 -> etY0.text.toString()
            else -> etEquation.text.toString()
        }
        val jsCmd = "setLatex(${jsonString(currentValue)});"
        wvMathLive.evaluateJavascript(jsCmd, null)
    }

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
            appendLine("Resuelve esta EDO con PRECISION MATEMATICA. Solo LaTeX.")
            appendLine("")
            appendLine("FORMATO obligatorio:")
            appendLine("ANTES de <<<PASOS>>>:")
            appendLine("\\[\\textbf{SOLUCION}\\]")
            appendLine("\\[\\textbf{TIPO:} \\text{clasificacion EDO}\\]")
            appendLine("\\[y = \\text{solucion exacta}\\]")
            appendLine("")
            appendLine("<<<PASOS>>>")
            appendLine("")
            appendLine("DESPUES de <<<PASOS>>>:")
            appendLine("\\[\\textbf{PASOS}\\]")
            appendLine("\\[\\textbf{METODO:} \\text{metodo usado}\\]")
            appendLine("(desarrollo paso a paso)")
            appendLine("")
            appendLine("IMPORTANTE: Verifica calculos. NO inventes soluciones.")
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
                                PplxMessage("system", "Experto matematico en EDO. Solo LaTeX preciso."),
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

                saveToHistory(
                    equation = equation,
                    x0 = x0.ifBlank { null },
                    y0 = y0.ifBlank { null },
                    solution = sol,
                    steps = steps
                )

            } catch (e: HttpException) {
                btnToggleSteps.isEnabled = false
                setResultLatex("\\[\\text{HTTP ${e.code()}: ${e.message()}}\\]")
            } catch (e: IOException) {
                btnToggleSteps.isEnabled = false
                setResultLatex("\\[\\text{Error de red}\\]")
            } catch (e: Exception) {
                btnToggleSteps.isEnabled = false
                setResultLatex("\\[\\text{Error: ${e.javaClass.simpleName}}\\]")
            }
        }
    }

    private fun splitSolutionSteps(allLatex: String): Pair<String, String> {
        val delim = "<<<PASOS>>>"
        val parts = allLatex.split(delim, limit = 2)
        val solution = if (parts.isNotEmpty()) parts[0].trim() else ""
        val steps = if (parts.size > 1) parts[1].trim() else ""
        val safeSolution = solution.ifBlank {
            "\\[\\textbf{SOLUCION}\\]\\n\\[\\textbf{TIPO:} \\text{(no disponible)}\\]"
        }
        val safeSteps = steps.ifBlank {
            "\\[\\textbf{PASOS}\\]\\n\\[\\textbf{METODO:} \\text{(no disponible)}\\]"
        }
        return Pair(safeSolution, safeSteps)
    }

    private fun setupWebView(wv: WebView) {
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()
        wv.loadDataWithBaseURL("https://cdn.jsdelivr.net/", baseMathJaxHtml(), "text/html", "utf-8", null)
    }

    private fun baseMathJaxHtml(): String {
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
                  font-size: 20px; 
                  padding: 12px; 
                  margin: 0; 
                  overflow-wrap: break-word;
                  word-wrap: break-word;
                  line-height: 1.6;
                  background: $bgColor;
                  color: $textColor;
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
