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
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var switchMathpix: Switch
    private lateinit var processingCard: CardView
    private lateinit var tvProcessing: TextView

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val mathpixApi by lazy {
        MathpixClient.create(BuildConfig.MATHPIX_APP_ID, BuildConfig.MATHPIX_APP_KEY)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de camara denegado", Toast.LENGTH_SHORT).show()
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
        switchMathpix = findViewById(R.id.switchMathpix)
        processingCard = findViewById(R.id.processingCard)
        tvProcessing = findViewById(R.id.tvProcessing)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Por defecto usar Mathpix si hay credenciales
        switchMathpix.isChecked = BuildConfig.MATHPIX_APP_ID.isNotBlank()

        btnCapture.setOnClickListener {
            takePhoto()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnGallery.setOnClickListener {
            openGallery()
        }

        checkCameraPermission()
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
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
        
        if (switchMathpix.isChecked && BuildConfig.MATHPIX_APP_ID.isNotBlank()) {
            tvProcessing.text = "Procesando con Mathpix..."
            processMathpix(bitmap)
        } else {
            tvProcessing.text = "Procesando con ML Kit..."
            processMLKit(bitmap)
        }
    }

    private fun processMathpix(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val base64 = bitmapToBase64(bitmap)
                val request = MathpixRequest(src = "data:image/jpeg;base64,$base64")
                
                val response = withContext(Dispatchers.IO) {
                    mathpixApi.recognizeImage(request)
                }

                if (response.error != null) {
                    Log.e("Mathpix", "Error: ${response.error}")
                    // Fallback a ML Kit
                    tvProcessing.text = "Mathpix fallo, usando ML Kit..."
                    processMLKit(bitmap)
                } else {
                    val latex = response.latex_styled ?: response.text ?: ""
                    if (latex.isBlank()) {
                        showError("No se detecto ecuacion")
                    } else {
                        returnResult(latex)
                    }
                }
            } catch (e: Exception) {
                Log.e("Mathpix", "Exception", e)
                // Fallback a ML Kit
                tvProcessing.text = "Error Mathpix, usando ML Kit..."
                processMLKit(bitmap)
            }
        }
    }

    private fun processMLKit(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        mlKitRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                Log.d("MLKit", "Text: $text")

                if (text.isBlank()) {
                    showError("No se detecto texto")
                    return@addOnSuccessListener
                }

                val latex = convertToLatex(text)
                returnResult(latex)
            }
            .addOnFailureListener { e ->
                Log.e("MLKit", "Failed", e)
                showError("Error: ${e.message}")
            }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Comprimir imagen para Mathpix
        val maxSize = 1024
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
        resized.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun convertToLatex(text: String): String {
        var latex = text.trim()
            .replace("\n", " ")
            .replace("  ", " ")
            .replace("'", "'")
            .replace("’", "'")
            .replace(" x ", "x")
            .replace(" y ", "y")
            .replace(" = ", "=")
            .replace(" + ", "+")
            .replace(" - ", "-")
            .replace(" * ", "\\times")
            .replace("*", "\\times")
            .replace(" / ", "\\div")
            .replace("^", "^{}")
            .replace("sin", "\\sin")
            .replace("cos", "\\cos")
            .replace("tan", "\\tan")
            .replace("ln", "\\ln")
            .replace("log", "\\log")
            .replace("sqrt", "\\sqrt{}")
            .replace("√", "\\sqrt{}")
        return latex
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
            switchMathpix.isEnabled = !show
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
