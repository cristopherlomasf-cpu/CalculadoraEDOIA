# Integración MathLive - Editor Matemático Interactivo

## ¿Qué es MathLive?

MathLive es una biblioteca JavaScript profesional que proporciona un editor matemático WYSIWYG (lo que ves es lo que obtienes). Permite editar ecuaciones matemáticas con un cursor interactivo que funciona en 2D.

## Características implementadas

### ✅ Editor Interactivo
- **Cursor visual** que se mueve entre fracciones, exponentes, raíces, etc.
- **Renderizado en tiempo real** de LaTeX
- **Navegación intuitiva** con flechas del teclado virtual
- **Edición natural** como en una calculadora científica profesional

### ✅ Integración con Android
- **Comunicación bidireccional** JavaScript ↔ Android usando `JavascriptInterface`
- **Sincronización automática** con EditText interno
- **Botones personalizados** para EDO (y', y'', dy/dx, etc.)
- **Cambio de campo** (EDO, x₀, y₀) preservando valores

### ✅ Teclado Virtual
- **Botones optimizados** para ecuaciones diferenciales
- **Inserción directa de LaTeX** en el editor
- **Funciones comunes**: fracciones, exponentes, raíces, trigonométricas
- **Teclas especiales**: backspace, clear, navegación

## Cómo funciona

### 1. Setup del WebView

```kotlin
private fun setupMathLiveEditor() {
    wvMathLive.settings.javaScriptEnabled = true
    wvMathLive.addJavascriptInterface(MathLiveInterface(), "Android")
    wvMathLive.loadDataWithBaseURL(
        "https://unpkg.com/",
        getMathLiveHtml(),
        "text/html",
        "utf-8",
        null
    )
}
```

### 2. HTML con MathLive CDN

```html
<math-field id="mathfield" virtual-keyboard-mode="manual">
</math-field>

<script type="module">
    import 'https://unpkg.com/mathlive@0.98.6/dist/mathlive.min.js';
    // ...
</script>
```

### 3. Comunicación JavaScript → Android

```javascript
// En MathLive
mf.addEventListener('input', () => {
    const latex = mf.value;
    Android.onLatexChanged(latex);
});
```

```kotlin
// En Android
inner class MathLiveInterface {
    @JavascriptInterface
    fun onLatexChanged(latex: String) {
        runOnUiThread {
            etEquation.setText(latex)
        }
    }
}
```

### 4. Comunicación Android → JavaScript

```kotlin
// Insertar LaTeX desde botones
val jsCmd = "insertLatex(${jsonString("\\frac{}{}")})"
wvMathLive.evaluateJavascript(jsCmd, null)
```

## Mapeo de botones

| Botón | LaTeX insertado | Resultado |
|--------|-----------------|------------|
| `y'` | `y'` | y′ |
| `y''` | `y''` | y″ |
| `dy/dx` | `\frac{dy}{dx}` | ẟẏ/Ẉẏ |
| `frac` | `\frac{}{}` | Fracción vacía |
| `x^2` | `^{}` | Exponente |
| `√x` | `\sqrt{}` | Raíz cuadrada |
| `e^x` | `e^{}` | Exponencial |
| `sin` | `\sin(` | Seno |

## Ventajas sobre el sistema anterior

### Antes (EditText + Preview)
- ❌ Cursor en texto plano
- ❌ Navegación lineal solamente
- ❌ Difícil editar fracciones complejas
- ❌ Vista previa separada del input

### Ahora (MathLive)
- ✅ Cursor inteligente 2D
- ✅ Navegación natural entre numeradores/denominadores
- ✅ Edición WYSIWYG en tiempo real
- ✅ Vista y edición unificadas

## Estructura de archivos modificados

```
app/src/main/
├── java/.../MainActivity.kt          # Lógica de MathLive + interface
├── res/layout/activity_main.xml    # Layout con wvMathLive
└── AndroidManifest.xml             # Permisos internet (para CDN)
```

## Ejemplo de uso

1. Usuario abre la app
2. Ve el editor MathLive vacío con cursor parpadeante
3. Presiona botón `dy/dx`
4. Aparece fracción dy/dx con cursor en el numerador
5. Escribe `2y`
6. Presiona `=`
7. Escribe `e^-x`
8. Resultado final en LaTeX: `\frac{dy}{dx}+2y=e^{-x}`
9. Presiona "Resolver"
10. API recibe LaTeX limpio y preciso

## Troubleshooting

### Editor no aparece
- Verificar conexión a internet (CDN de MathLive)
- Revisar logs: `adb logcat | grep MathLive`
- Verificar que JavaScript está habilitado en WebView

### Botón no inserta LaTeX
- Verificar que `isMathLiveReady == true`
- Revisar escape de caracteres en `jsonString()`
- Verificar nombre correcto de función JS

### Valores no se sincronizan
- Verificar que `@JavascriptInterface` está presente
- Confirmar que `addJavascriptInterface` se llama antes de `loadData`
- Revisar que `runOnUiThread` se usa en callbacks

## Siguientes mejoras posibles

- [ ] Teclado virtual de MathLive nativo (en lugar de botones)
- [ ] Historial de ecuaciones
- [ ] Copiar/pegar LaTeX
- [ ] Modo offline (bundlear MathLive localmente)
- [ ] Personalización de colores/tamaños
- [ ] Autocompletado inteligente

## Referencias

- [MathLive Docs](https://cortexjs.io/mathlive/)
- [MathLive GitHub](https://github.com/arnog/mathlive)
- [Android WebView Guide](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [JavascriptInterface](https://developer.android.com/reference/android/webkit/JavascriptInterface)
