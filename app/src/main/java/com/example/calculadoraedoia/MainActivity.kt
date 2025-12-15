package com.example.calculadoraedoia

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class MainActivity : AppCompatActivity(), KeyboardPageFragment.KeyClickListener {

    private val api by lazy { PerplexityClient.create(BuildConfig.PPLX_API_KEY) }

    private lateinit var etEquation: EditText
    private lateinit var etX0: EditText
    private lateinit var etY0: EditText

    private lateinit var wvPreviewTop: WebView
    private lateinit var wvResult: WebView

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: KeyboardPagerAdapter

    private lateinit var btnPvi: Button
    private lateinit var btnSolve: Button
    private lateinit var btnToggleSteps: Button

    // 0=EDO, 1=x0, 2=y0
    private var activeField = 0

    // Preview debounce
    private val previewDelayMs = 120L
    private var previewRunnable: Runnable? = null

    // Toggle pasos
    private var showSteps = false
    private var lastSolutionLatex: String? = null
    private var lastStepsLatex: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etEquation = findViewById(R.id.etEquation)
        etX0 = findViewById(R.id.etX0)
        etY0 = findViewById(R.id.etY0)

        wvPreviewTop = findViewById(R.id.wvPreviewTop)
        wvResult = findViewById(R.id.wvLatexResult)

        tabLayout = findViewById(R.id.tabKeyboard)
        viewPager = findViewById(R.id.vpKeyboard)

        btnPvi = findViewById(R.id.btnPvi)
        btnSolve = findViewById(R.id.btnSolve)
        btnToggleSteps = findViewById(R.id.btnToggleSteps)
        btnToggleSteps.text = "Ver pasos"

        // No teclado del sistema
        etEquation.showSoftInputOnFocus = false
        etX0.showSoftInputOnFocus = false
        etY0.showSoftInputOnFocus = false

        setupWebView(wvPreviewTop)
        setupWebView(wvResult)

        pagerAdapter = KeyboardPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = pagerAdapter.title(pos)
        }.attach()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { schedulePreview() }
            override fun afterTextChanged(s: Editable?) {}
        }
        etEquation.addTextChangedListener(watcher)
        etX0.addTextChangedListener(watcher)
        etY0.addTextChangedListener(watcher)

        btnPvi.setOnClickListener {
            activeField = when (activeField) {
                0 -> 1
                1 -> 2
                else -> 0
            }
            updatePviButtonLabel()
            schedulePreview()
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
        updatePviButtonLabel()
        btnToggleSteps.text = "Ver pasos"
        btnToggleSteps.isEnabled = false
        renderPreview()
        setResultLatex("\\[\\text{Presiona Resolver.}\\]")
    }

    private fun updatePviButtonLabel() {
        btnPvi.text = when (activeField) {
            1 -> "PVI: x0"
            2 -> "PVI: y0"
            else -> "PVI"
        }
    }

    private fun currentField(): EditText = when (activeField) {
        1 -> etX0
        2 -> etY0
        else -> etEquation
    }

    private fun insertText(s: String) {
        val et = currentField()
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(0)
        et.text.replace(minOf(start, end), maxOf(start, end), s, 0, s.length)
        et.setSelection(minOf(start, end) + s.length)
        schedulePreview()
    }

    private fun insertTemplate(before: String, inside: String, after: String) {
        val et = currentField()
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(0)
        val selected = et.text.substring(minOf(start, end), maxOf(start, end))
        val payload = before + (if (selected.isNotEmpty()) selected else inside) + after
        et.text.replace(minOf(start, end), maxOf(start, end), payload, 0, payload.length)
        val cursorPos = minOf(start, end) + before.length +
                (if (selected.isNotEmpty()) selected.length else inside.length)
        et.setSelection(cursorPos)
        schedulePreview()
    }

    private fun backspace() {
        val et = currentField()
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(0)
        if (start != end) {
            et.text.delete(minOf(start, end), maxOf(start, end))
            et.setSelection(minOf(start, end))
        } else if (start > 0) {
            et.text.delete(start - 1, start)
            et.setSelection(start - 1)
        }
        schedulePreview()
    }

    override fun onKeyClicked(id: Int) {
        when (id) {
            // y' y''
            R.id.kb_y1 -> { activeField = 0; updatePviButtonLabel(); insertText("y'") }
            R.id.kb_y2 -> { activeField = 0; updatePviButtonLabel(); insertText("y''") }

            // BASIC
            R.id.kb_0 -> insertText("0")
            R.id.kb_1 -> insertText("1")
            R.id.kb_2 -> insertText("2")
            R.id.kb_3 -> insertText("3")
            R.id.kb_4 -> insertText("4")
            R.id.kb_5 -> insertText("5")
            R.id.kb_6 -> insertText("6")
            R.id.kb_7 -> insertText("7")
            R.id.kb_8 -> insertText("8")
            R.id.kb_9 -> insertText("9")

            R.id.kb_plus -> insertText("+")
            R.id.kb_minus -> insertText("-")
            R.id.kb_mul -> insertText("*")
            R.id.kb_div -> insertText("/")
            R.id.kb_pow -> insertText("^")
            R.id.kb_eq -> { activeField = 0; updatePviButtonLabel(); insertText("=") }
            R.id.kb_lpar -> insertText("(")
            R.id.kb_rpar -> insertText(")")
            R.id.kb_dot -> insertText(".")
            R.id.kb_comma -> {
                if (activeField == 1) {
                    activeField = 2
                    updatePviButtonLabel()
                    schedulePreview()
                } else insertText(",")
            }

            R.id.kb_frac -> { insertTemplate("(", "a", ")/("); insertText("b)") }
            R.id.kb_back -> backspace()
            R.id.kb_clear -> { val et = currentField(); et.setText(""); et.setSelection(0); schedulePreview() }

            // FUNCS
            R.id.kb_sin -> insertTemplate("sin(", "x", ")")
            R.id.kb_cos -> insertTemplate("cos(", "x", ")")
            R.id.kb_tan -> insertTemplate("tan(", "x", ")")
            R.id.kb_asin -> insertTemplate("asin(", "x", ")")
            R.id.kb_acos -> insertTemplate("acos(", "x", ")")
            R.id.kb_atan -> insertTemplate("atan(", "x", ")")
            R.id.kb_ln -> insertTemplate("ln(", "x", ")")
            R.id.kb_log -> insertTemplate("log(", "x", ")")
            R.id.kb_exp -> insertTemplate("exp(", "x", ")")
            R.id.kb_sqrt -> insertTemplate("sqrt(", "x", ")")
            R.id.kb_abs -> insertTemplate("abs(", "x", ")")
            R.id.kb_fact -> insertText("!")
            R.id.kb_pi -> insertText("pi")
            R.id.kb_e -> insertText("e")
            R.id.kb_x -> insertText("x")
            R.id.kb_y -> insertText("y")
            R.id.kb_t -> insertText("t")
            R.id.kb_z -> insertText("z")

            // CALC/EDO
            R.id.kb_dydx -> { activeField = 0; updatePviButtonLabel(); insertText("dy/dx") }
            R.id.kb_dx -> { activeField = 0; updatePviButtonLabel(); insertText("dx") }
            R.id.kb_dy -> { activeField = 0; updatePviButtonLabel(); insertText("dy") }
            R.id.kb_int -> { activeField = 0; updatePviButtonLabel(); insertTemplate("int(", "f(x)", ",x)") }
            R.id.kb_ddx -> { activeField = 0; updatePviButtonLabel(); insertTemplate("d/dx(", "f(x)", ")") }
            R.id.kb_partial -> insertText("∂")
            R.id.kb_infty -> insertText("∞")
            R.id.kb_leq -> insertText("<=")
            R.id.kb_geq -> insertText(">=")
            R.id.kb_neq -> insertText("!=")
            R.id.kb_pm -> insertText("+/-")
        }
    }

    // -------- Preview --------

    private fun schedulePreview() {
        previewRunnable?.let { wvPreviewTop.removeCallbacks(it) }
        previewRunnable = Runnable { renderPreview() }
        wvPreviewTop.postDelayed(previewRunnable!!, previewDelayMs)
    }

    private fun renderPreview() {
        val eq = etEquation.text.toString().trim()
        val x0 = etX0.text.toString().trim()
        val y0 = etY0.text.toString().trim()

        val showPvi = (x0.isNotBlank() || y0.isNotBlank() || activeField == 1 || activeField == 2)

        val eqLatex = toLatexApprox(if (eq.isBlank()) "\\text{(EDO vacía)}" else eq)
        val xLatex = toLatexApprox(if (x0.isBlank()) "?" else x0)
        val yLatex = toLatexApprox(if (y0.isBlank()) "?" else y0)

        val latex = buildString {
            append("\\["); append(eqLatex); append("\\]")
            if (showPvi) {
                append("\\[x_0=")
                append(if (activeField == 1) "\\boxed{$xLatex}" else xLatex)
                append("\\qquad y_0=")
                append(if (activeField == 2) "\\boxed{$yLatex}" else yLatex)
                append("\\]")
            }
        }

        val js = """
            (function(){
              const tex = ${jsonString(latex)};
              const el = document.getElementById('math');
              if (window.MathJax && MathJax.typesetClear) MathJax.typesetClear([el]);
              el.textContent = tex;
              if (window.MathJax && MathJax.typesetPromise) MathJax.typesetPromise([el]);
            })();
        """.trimIndent()

        wvPreviewTop.evaluateJavascript(js, null)
    }

    // -------- Resultado + Toggle pasos --------

    private fun renderResultFromCache() {
        val sol = lastSolutionLatex
        if (sol == null) {
            setResultLatex("\\[\\text{No hay solución aún.}\\]")
            return
        }
        if (!showSteps) {
            setResultLatex(sol)
        } else {
            val steps = lastStepsLatex ?: "\\[\\text{(Sin pasos)}\\]"
            setResultLatex(sol + "\n" + steps)
        }
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

        // Reset toggle
        showSteps = false
        btnToggleSteps.text = "Ver pasos"
        btnToggleSteps.isEnabled = false
        lastSolutionLatex = null
        lastStepsLatex = null

        setResultLatex("\\[\\text{Resolviendo...}\\]")

        // Prompt mejorado con formato vertical
        val prompt = buildString {
            appendLine("Resuelve esta EDO y devuelve SOLO LaTeX para MathJax. NO uses bloques de código (```latex).")
            appendLine("")
            appendLine("FORMATO OBLIGATORIO:")
            appendLine("Parte 1 - SOLUCIÓN:")
            appendLine("\\[\\textbf{SOLUCION}\\]")
            appendLine("\\[\\textbf{TIPO:}\\ \\text{tipo de EDO}\\]")
            appendLine("\\[y = \\text{solución final}\\]")
            appendLine("")
            appendLine("<<<PASOS>>>")
            appendLine("")
            appendLine("Parte 2 - PASOS (cada paso en su propia línea \\[...\\]):")
            appendLine("\\[\\textbf{PASOS}\\]")
            appendLine("\\[\\textbf{METODO:}\\ \\text{método usado}\\]")
            appendLine("\\[\\text{Paso 1: Breve descripción}\\]")
            appendLine("\\[ecuación \\ o \\ resultado \\ del \\ paso \\ 1\\]")
            appendLine("\\[\\text{Paso 2: Breve descripción}\\]")
            appendLine("\\[ecuación \\ o \\ resultado \\ del \\ paso \\ 2\\]")
            appendLine("(continúa con más pasos hasta resolver)")
            appendLine("")
            appendLine("REGLAS:")
            appendLine("- NUNCA uses bloques de código ```latex")
            appendLine("- Cada paso debe tener 2 líneas: descripción y ecuación")
            appendLine("- Cada línea debe estar en su propio \\[...\\]")
            appendLine("- Usa espacios \\ entre palabras largas para evitar desbordamiento")
            appendLine("- Si no puedes resolver, deja en función de C")
            appendLine("")
            appendLine("EDO: $equation")
            if (hasPvi) appendLine("PVI: x0=$x0, y0=$y0")
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.chat(
                        PplxRequest(
                            model = "sonar-pro",
                            messages = listOf(
                                PplxMessage("system", "Devuelve únicamente LaTeX válido para MathJax. NO uses bloques de código."),
                                PplxMessage("user", prompt)
                            ),
                            temperature = 0.2
                        )
                    )
                }

                val raw = resp.choices.firstOrNull()?.message?.content ?: ""
                // Limpiar bloques de código y etiquetas think
                val cleaned = raw
                    .replace(Regex("(?s)<think>.*?</think>\\s*"), "")
                    .replace("```latex", "")
                    .replace("```", "")
                    .trim()

                val (sol, steps) = splitSolutionSteps(cleaned)
                lastSolutionLatex = sol
                lastStepsLatex = steps

                btnToggleSteps.isEnabled = true
                renderResultFromCache()

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
        val solution = allLatex.substringBefore(delim).trim()
        val steps = allLatex.substringAfter(delim, missingDelimiterValue = "").trim()

        val safeSolution = solution.ifBlank {
            "\\[\\textbf{SOLUCION}\\]\n\\[\\textbf{TIPO:}\\ \\text{(no disponible)}\\]"
        }
        val safeSteps = steps.ifBlank {
            "\\[\\textbf{PASOS}\\]\n\\[\\textbf{METODO:}\\ \\text{(no disponible)}\\]"
        }
        return Pair(safeSolution, safeSteps)
    }



    // -------- WebView base MathJax --------

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
                  font-size: 18px; 
                  padding: 8px; 
                  margin: 0; 
                  overflow-wrap: break-word;
                  word-wrap: break-word;
                } 
              </style>
              <script>
                window.MathJax = { tex: { inlineMath: [['\\\(','\\\)']], displayMath: [['\\\[','\\\]']] } };
              </script>
              <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            </head>
            <body>
              <div id="math"></div>
            </body>
            </html>
        """.trimIndent()
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

    private fun toLatexApprox(s: String): String {
        return s
            .replace("y''", "y^{\\prime\\prime}")
            .replace("y'", "y^{\\prime}")
            .replace("pi", "\\pi")
            .replace("dy/dx", "\\frac{dy}{dx}")
            .replace("*", "\\cdot ")
            .replace("<=", "\\le ")
            .replace(">=", "\\ge ")
            .replace("!=", "\\ne ")
            .replace("+/-", "\\pm ")
            .replace(Regex("""sqrt\(([^()]*)\)""")) { m -> "\\sqrt{${m.groupValues[1]}}" }
    }
}
