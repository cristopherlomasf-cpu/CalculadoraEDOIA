package com.example.calculadoraedoia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraOcrActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: FloatingActionButton
    private lateinit var btnCancel: Button
    private lateinit var btnGallery: Button
    private lateinit var btnManualInput: Button
    private lateinit var switchMathpix: Switch
    private lateinit var processingCard: CardView
    private lateinit var tvProcessing: TextView

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    // Google Vision API
    private val googleVisionApi by lazy {
        if (BuildConfig.GOOGLE_VISION_API_KEY.isNotBlank()) {
            GoogleVisionClient.create(BuildConfig.GOOGLE_VISION_API_KEY)
        } else null
    }
    
    // Mathpix (opcional)
    private val mathpixApi by lazy {
        if (BuildConfig.MATHPIX_APP_ID.isNotBlank()) {
            MathpixClient.create(BuildConfig.MATHPIX_APP_ID, BuildConfig.MATHPIX_APP_KEY)
        } else null
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                processImageFromGallery(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_ocr)

        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        btnCancel = findViewById(R.id.btnCancel)
        btnGallery = findViewById(R.id.btnGallery)
        btnManualInput = findViewById(R.id.btnManualInput)
        switchMathpix = findViewById(R.id.switchMathpix)
        processingCard = findViewById(R.id.processingCard)
        tvProcessing = findViewById(R.id.tvProcessing)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configurar switch (ahora es Google Vision vs ML Kit)
        val hasGoogleVision = BuildConfig.GOOGLE_VISION_API_KEY.isNotBlank()
        val hasMathpix = BuildConfig.MATHPIX_APP_ID.isNotBlank()
        
        if (hasGoogleVision || hasMathpix) {
            switchMathpix.isChecked = true
            switchMathpix.isEnabled = true
            // Cambiar texto del switch
            findViewById<TextView>(R.id.tvProcessing)?.parent?.let {
                // El switch ahora representa "OCR Premium" (Google Vision o Mathpix)
            }
        } else {
            switchMathpix.isChecked = false
            switchMathpix.visibility = View.GONE
            Toast.makeText(
                this,
                "⚠️ Usando ML Kit básico. Agrega Google Vision API key para mejor detección.",
                Toast.LENGTH_LONG
            ).show()
        }

        btnCapture.setOnClickListener {
            takePhoto()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnGallery.setOnClickListener {
            openGallery()
        }
        
        btnManualInput.setOnClickListener {
            showManualInputDialog()
        }

        // Verificar si viene de URI de imagen directamente
        val imageUriString = intent.getStringExtra("image_uri")
        if (!imageUriString.isNullOrBlank()) {
            val uri = Uri.parse(imageUriString)
            processImageFromGallery(uri)
        } else {
            checkCameraPermission()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraOCR", "Camera binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    if (bitmap != null) {
                        processImage(bitmap)
                    } else {
                        showError("Error al capturar imagen")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraOCR", "Photo capture failed", exception)
                    showError("Error: ${exception.message}")
                }
            }
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        
        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun processImageFromGallery(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            processImage(bitmap)
        } catch (e: Exception) {
            Log.e("CameraOCR", "Gallery image processing failed", e)
            showError("Error al procesar imagen")
        }
    }

    private fun processImage(bitmap: Bitmap) {
        showProcessing(true)
        
        val processedBitmap = preprocessImage(bitmap)
        
        // Prioridad: Mathpix > Google Vision > ML Kit
        when {
            switchMathpix.isChecked && mathpixApi != null -> {
                tvProcessing.text = "🔍 Analizando con Mathpix PRO..."
                processMathpix(processedBitmap)
            }
            switchMathpix.isChecked && googleVisionApi != null -> {
                tvProcessing.text = "🔍 Analizando con Google Vision..."
                processGoogleVision(processedBitmap)
            }
            else -> {
                tvProcessing.text = "🔍 Detectando con ML Kit..."
                processMLKit(processedBitmap)
            }
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        val maxDimension = 2048
        val scale = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height,
            1f
        )
        
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
    }

    private fun processGoogleVision(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val base64 = bitmapToBase64(bitmap)
                
                val request = VisionRequest(
                    requests = listOf(
                        AnnotateImageRequest(
                            image = Image(content = base64),
                            features = listOf(
                                Feature(type = "TEXT_DETECTION", maxResults = 1)
                            )
                        )
                    )
                )
                
                val response = withContext(Dispatchers.IO) {
                    googleVisionApi!!.annotateImage(BuildConfig.GOOGLE_VISION_API_KEY, request)
                }

                val firstResponse = response.responses.firstOrNull()
                
                if (firstResponse?.error != null) {
                    Log.e("GoogleVision", "Error: ${firstResponse.error.message}")
                    showError("❌ Google Vision: ${firstResponse.error.message}")
                } else {
                    val text = firstResponse?.textAnnotations?.firstOrNull()?.description 
                        ?: firstResponse?.fullTextAnnotation?.text 
                        ?: ""
                    
                    Log.d("GoogleVision", "Detected text: $text")
                    
                    if (text.isBlank()) {
                        showErrorWithManualOption("No se detectó texto")
                    } else {
                        val latex = improvedTextToLatex(text)
                        showConfirmationDialog(latex, text)
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleVision", "Exception", e)
                // Fallback a ML Kit
                tvProcessing.text = "🔍 Error Google Vision, usando ML Kit..."
                processMLKit(bitmap)
            }
        }
    }

    private fun processMathpix(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val base64 = bitmapToBase64(bitmap)
                val request = MathpixRequest(
                    src = "data:image/jpeg;base64,$base64",
                    formats = listOf("latex_styled", "text")
                )
                
                val response = withContext(Dispatchers.IO) {
                    mathpixApi!!.recognizeImage(request)
                }

                if (response.error != null) {
                    Log.e("Mathpix", "Error: ${response.error}")
                    // Fallback a Google Vision o ML Kit
                    if (googleVisionApi != null) {
                        tvProcessing.text = "🔍 Error Mathpix, usando Google Vision..."
                        processGoogleVision(bitmap)
                    } else {
                        tvProcessing.text = "🔍 Error Mathpix, usando ML Kit..."
                        processMLKit(bitmap)
                    }
                } else {
                    val latex = response.latex_styled ?: response.text ?: ""
                    Log.d("Mathpix", "Detected: $latex")
                    
                    if (latex.isBlank()) {
                        showErrorWithManualOption("No se detectó ecuación")
                    } else {
                        returnResult(latex)
                    }
                }
            } catch (e: Exception) {
                Log.e("Mathpix", "Exception", e)
                // Fallback
                if (googleVisionApi != null) {
                    processGoogleVision(bitmap)
                } else {
                    processMLKit(bitmap)
                }
            }
        }
    }

    private fun processMLKit(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        mlKitRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                Log.d("MLKit", "Raw text: $text")

                if (text.isBlank()) {
                    showErrorWithManualOption("No se detectó texto")
                    return@addOnSuccessListener
                }

                val latex = improvedTextToLatex(text)
                Log.d("MLKit", "Converted: $latex")
                showConfirmationDialog(latex, text)
            }
            .addOnFailureListener { e ->
                Log.e("MLKit", "Failed", e)
                showError("❌ Error: ${e.message}")
            }
    }

    private fun improvedTextToLatex(text: String): String {
        var latex = text.trim()
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            
        // Detectar derivadas
        latex = latex
            .replace(Regex("dy\\s*/\\s*dx"), "\\frac{dy}{dx}")
            .replace(Regex("d\\^?2y\\s*/\\s*dx\\^?2"), "\\frac{d^2y}{dx^2}")
            .replace("y''", "y''")
            .replace("y'", "y'")
            
        // Fracciones
        latex = latex.replace(Regex("(\\d+)\\s*/\\s*(\\d+)")) { match ->
            "\\frac{${match.groupValues[1]}}{${match.groupValues[2]}}"
        }
        
        // Operadores
        latex = latex
            .replace("*", "\\cdot")
            .replace("x", "\\cdot")
            
        // Funciones
        latex = latex
            .replace(Regex("\\bsin\\b"), "\\sin")
            .replace(Regex("\\bcos\\b"), "\\cos")
            .replace(Regex("\\btan\\b"), "\\tan")
            .replace(Regex("\\bln\\b"), "\\ln")
            .replace(Regex("\\blog\\b"), "\\log")
            .replace(Regex("\\bexp\\b"), "\\exp")
            
        // Raíces
        latex = latex
            .replace("sqrt", "\\sqrt")
            .replace("√", "\\sqrt")
            
        return latex
    }

    private fun showConfirmationDialog(latex: String, originalText: String) {
        runOnUiThread {
            showProcessing(false)
            
            AlertDialog.Builder(this)
                .setTitle("📝 Texto detectado")
                .setMessage("Texto: $originalText\n\nConvertido: $latex\n\n¿Usar esta ecuación?")
                .setPositiveButton("Usar") { _, _ ->
                    returnResult(latex)
                }
                .setNeutralButton("Editar") { _, _ ->
                    showManualInputDialog(latex)
                }
                .setNegativeButton("Reintentar", null)
                .show()
        }
    }

    private fun showManualInputDialog(initialText: String = "") {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Ej: dy/dx + 2y = x^2"
        input.setText(initialText)
        
        AlertDialog.Builder(this)
            .setTitle("✏️ Escribir ecuación manualmente")
            .setMessage("Escribe la ecuación diferencial:")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    returnResult(improvedTextToLatex(text))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showErrorWithManualOption(message: String) {
        runOnUiThread {
            showProcessing(false)
            
            AlertDialog.Builder(this)
                .setTitle("❌ $message")
                .setMessage("¿Qué deseas hacer?")
                .setPositiveButton("Escribir manual") { _, _ ->
                    showManualInputDialog()
                }
                .setNegativeButton("Reintentar", null)
                .show()
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxSize = 1600
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val resized = if (ratio < 1) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else bitmap

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 92, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun returnResult(latex: String) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_LATEX_RESULT, latex)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun showProcessing(show: Boolean) {
        runOnUiThread {
            processingCard.visibility = if (show) View.VISIBLE else View.GONE
            btnCapture.isEnabled = !show
            btnGallery.isEnabled = !show
            btnManualInput.isEnabled = !show
            switchMathpix.isEnabled = !show && (googleVisionApi != null || mathpixApi != null)
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            showProcessing(false)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mlKitRecognizer.close()
    }

    companion object {
        const val EXTRA_LATEX_RESULT = "latex_result"
    }
}
