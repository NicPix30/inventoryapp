package com.application.inventoryapp

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var isProcessingBarcode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e("ScanActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && !isProcessingBarcode) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null) {
                            isProcessingBarcode = true
                            checkBarcodeInFirebase(rawValue)
                            break
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("ScanActivity", "Barcode scanning failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun checkBarcodeInFirebase(barcode: String) {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("scanned_items").child(barcode)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Item exists - get name and return
                    val itemName = snapshot.child("name").getValue(String::class.java) ?: barcode
                    returnResult(itemName)
                } else {
                    // Item doesn't exist - prompt user for name
                    promptForName(barcode)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ScanActivity", "Firebase DB error: ${error.message}")
                Toast.makeText(this@ScanActivity, "DB error: ${error.message}", Toast.LENGTH_SHORT).show()
                isProcessingBarcode = false
            }
        })
    }

    private fun promptForName(barcode: String) {
        runOnUiThread {
            val editText = EditText(this)
            editText.hint = "Enter item name"

            AlertDialog.Builder(this)
                .setTitle("New Item")
                .setMessage("Item with barcode $barcode not found. Enter item name:")
                .setView(editText)
                .setCancelable(false)
                .setPositiveButton("Save") { dialog, _ ->
                    val name = editText.text.toString().trim()
                    if (name.isNotEmpty()) {
                        saveNewItem(barcode, name)
                    } else {
                        Toast.makeText(this, "Name can't be empty", Toast.LENGTH_SHORT).show()
                        isProcessingBarcode = false
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    isProcessingBarcode = false
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun saveNewItem(barcode: String, name: String) {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("scanned_items").child(barcode)

        ref.setValue(mapOf("name" to name))
            .addOnSuccessListener {
                Log.d("Firebase", "Saved new item: $barcode -> $name")
                returnResult(name)
            }
            .addOnFailureListener {
                Log.e("Firebase", "Failed to save new item", it)
                Toast.makeText(this, "Failed to save item", Toast.LENGTH_SHORT).show()
                isProcessingBarcode = false
            }
    }

    private fun returnResult(nameOrBarcode: String) {
        val intent = Intent()
        intent.putExtra("barcode", nameOrBarcode)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
