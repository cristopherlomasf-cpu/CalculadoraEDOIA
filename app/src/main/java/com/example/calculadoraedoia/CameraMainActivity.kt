package com.example.calculadoraedoia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraMainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var fabCapture: MaterialButton
    private lateinit var fabGallery: MaterialButton
    private lateinit var fabManual: MaterialButton
    private lateinit var btnHistory: MaterialButton
    private lateinit var btnDarkMode: MaterialButton
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var tvLoadingText: TextView

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processImageFromGallery(it)
        }
    }

    private val ocrResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val latex = result.data?.getStringExtra(CameraOcrActivity.EXTRA_LATEX_RESULT)
            if (!latex.isNullOrBlank()) {
                openMainActivityWithEquation(latex)
            }
        }
        hideLoading()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_main)

        initViews()
        setupListeners()
        checkCameraPermission()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        fabCapture = findViewById(R.id.fabCapture)
        fabGallery = findViewById(R.id.fabGallery)
        fabManual = findViewById(R.id.fabManual)
        btnHistory = findViewById(R.id.btnHistory)
        btnDarkMode = findViewById(R.id.btnDarkMode)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvLoadingText = findViewById(R.id.tvLoadingText)
    }

    private fun setupListeners() {
        fabCapture.setOnClickListener {
            capturePhoto()
        }

        fabGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        fabManual.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        btnDarkMode.setOnClickListener {
            toggleDarkMode()
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
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

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
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Error al iniciar cámara", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        showLoading("Capturando...")

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    hideLoading()
                    Toast.makeText(
                        this@CameraMainActivity,
                        "Error al capturar: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processImageFile(photoFile)
                }
            }
        )
    }

    private fun processImageFile(file: File) {
        showLoading("Analizando ecuación...")
        val uri = Uri.fromFile(file)
        val intent = Intent(this, CameraOcrActivity::class.java)
        intent.putExtra("image_uri", uri.toString())
        ocrResultLauncher.launch(intent)
    }

    private fun processImageFromGallery(uri: Uri) {
        showLoading("Analizando ecuación...")
        val intent = Intent(this, CameraOcrActivity::class.java)
        intent.putExtra("image_uri", uri.toString())
        ocrResultLauncher.launch(intent)
    }

    private fun openMainActivityWithEquation(latex: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("latex_equation", latex)
        startActivity(intent)
    }

    private fun showLoading(message: String) {
        tvLoadingText.text = message
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
    }

    private fun toggleDarkMode() {
        val currentMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val newMode = if (currentMode == Configuration.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
