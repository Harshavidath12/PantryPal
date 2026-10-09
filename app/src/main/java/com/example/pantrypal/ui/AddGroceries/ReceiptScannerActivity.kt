package com.example.pantrypal.ui.AddGroceries

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.pantrypal.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class ReceiptScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var btnCaptureReceipt: TextView
    private lateinit var btnPickGallery: TextView
    private lateinit var btnDemoReceipt: TextView

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to scan receipts", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                processGalleryImage(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_scanner)

        previewView = findViewById(R.id.receiptPreview)
        progressOverlay = findViewById(R.id.progressOverlay)
        btnCaptureReceipt = findViewById(R.id.btnCaptureReceipt)
        btnPickGallery = findViewById(R.id.btnPickGallery)
        btnDemoReceipt = findViewById(R.id.btnDemoReceipt)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnCaptureReceipt.setOnClickListener {
            captureAndProcessReceipt()
        }

        btnPickGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // Demo receipt for instant testing without paper receipts
        btnDemoReceipt.setOnClickListener {
            val sampleItems = listOf(
                "Organic Whole Milk 1L",
                "Fresh Strawberries",
                "Greek Yogurt",
                "Sourdough Bread",
                "Extra Virgin Olive Oil"
            )
            AlertDialog.Builder(this)
                .setTitle("Demo Receipt Items (${sampleItems.size})")
                .setItems(sampleItems.toTypedArray()) { _, which ->
                    returnResult(sampleItems[which], sampleItems)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to start camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndProcessReceipt() {
        val capture = imageCapture ?: return
        showLoading(true)

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()

                    if (bitmap != null) {
                        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
                        runOcr(inputImage)
                    } else {
                        runOnUiThread {
                            showLoading(false)
                            Toast.makeText(this@ReceiptScannerActivity, "Failed to capture image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this@ReceiptScannerActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun processGalleryImage(uri: Uri) {
        showLoading(true)
        try {
            val inputImage = InputImage.fromFilePath(this, uri)
            runOcr(inputImage)
        } catch (e: Exception) {
            showLoading(false)
            Toast.makeText(this, "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runOcr(image: InputImage) {
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                runOnUiThread {
                    showLoading(false)
                    handleExtractedText(visionText)
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this, "Text recognition failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun handleExtractedText(visionText: Text) {
        val allRawLines = visionText.textBlocks.flatMap { it.lines }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }

        if (allRawLines.isEmpty()) {
            Toast.makeText(
                this,
                "No text detected. Please ensure the receipt is well-lit and in focus.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val candidates = parseReceiptItems(allRawLines)

        if (candidates.isEmpty()) {
            // As fallback, take lines that are between 4 and 40 chars, contain letters, and don't match card/slip filters
            val fallbackCandidates = allRawLines.mapNotNull { cleanGroceryLine(it) }
            if (fallbackCandidates.isNotEmpty()) {
                candidates.addAll(fallbackCandidates.distinct())
            }
        }

        if (candidates.isEmpty()) {
            Toast.makeText(
                this,
                "No grocery items detected. Please align the receipt items and try again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // If exactly 1 grocery item detected (e.g. Nipuna Samba Rice 1kg), auto-fill immediately without popup!
        if (candidates.size == 1) {
            returnResult(candidates.first(), candidates)
        } else {
            // If multiple items, show picker dialog. If cancelled, fallback to first item so form is never blank!
            AlertDialog.Builder(this)
                .setTitle("Select Grocery Item (${candidates.size})")
                .setItems(candidates.toTypedArray()) { _, which ->
                    returnResult(candidates[which], candidates)
                }
                .setPositiveButton("Select '${candidates.first()}'") { _, _ ->
                    returnResult(candidates.first(), candidates)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    // Fallback to first item so user never gets a blank form
                    returnResult(candidates.first(), candidates)
                }
                .setOnCancelListener {
                    // Fallback to first item on outside tap/back press
                    returnResult(candidates.first(), candidates)
                }
                .show()
        }
    }

    /**
     * Filters out store headers, totals, taxes, card slips, and prices to extract actual grocery item names.
     */
    private fun parseReceiptItems(lines: List<String>): MutableList<String> {
        val items = mutableListOf<String>()

        for (line in lines) {
            val cleaned = cleanGroceryLine(line)
            if (cleaned != null && !items.contains(cleaned)) {
                items.add(cleaned)
            }
        }

        return items
    }

    /**
     * Cleans and validates a single line. Returns null if it's non-food, metadata, slip, or noise.
     */
    private fun cleanGroceryLine(raw: String): String? {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase(java.util.Locale.US)

        // 1. Filter out credit/debit card slip tokens & terminal messages
        if (lower.contains("card") || lower.contains("ntba") || lower.contains("slip") ||
            lower.contains("visa") || lower.contains("master") || lower.contains("amex") ||
            lower.contains("debit") || lower.contains("credit") || lower.contains("auth") ||
            lower.contains("approval") || lower.contains("batch") || lower.contains("trace") ||
            lower.contains("host") || lower.contains("pos") || lower.contains("mid:") ||
            lower.contains("tid:") || lower.contains("chip") || lower.contains("swipe") ||
            lower.contains("pan") || lower.contains("cvv") || lower.contains("sale") ||
            lower.contains("settlement") || lower.contains("merchant") || lower.contains("terminal") ||
            lower.contains("signature") || lower.contains("cardholder") || lower.contains("duplicate") ||
            lower.contains("customer copy") || lower.contains("merchant copy")
        ) {
            return null
        }

        // 2. Filter out store brand headers, addresses, telephone numbers
        if (lower.contains("keells") || lower.contains("cargills") || lower.contains("arpico") ||
            lower.contains("laugfs") || lower.contains("spar") || lower.contains("supermarket") ||
            lower.contains("walmart") || lower.contains("target") || lower.contains("costco") ||
            lower.contains("road") || lower.contains("street") || lower.contains("lane") ||
            lower.contains("avenue") || lower.contains("maharagama") || lower.contains("piliyandala") ||
            lower.contains("colombo") || lower.contains("branch") || lower.contains("store") ||
            lower.contains("hotline") || lower.contains("tel:") || lower.contains("tel.") ||
            lower.contains("fax") || lower.contains("email") || lower.contains("www.") ||
            lower.contains("http") || lower.contains("thank") || lower.contains("welcome") ||
            lower.contains("again") || lower.contains("cashier") || lower.contains("counter")
        ) {
            return null
        }

        // 3. Filter out billing / tax / payment calculation tokens
        if (lower.contains("total") || lower.contains("subtotal") || lower.contains("tax") ||
            lower.contains("vat") || lower.contains("nbt") || lower.contains("sscl") ||
            lower.contains("cash") || lower.contains("change") || lower.contains("disc") ||
            lower.contains("discount") || lower.contains("savings") || lower.contains("rounding") ||
            lower.contains("balance") || lower.contains("points") || lower.contains("earned") ||
            lower.contains("loyalty") || lower.contains("nexus") || lower.contains("redeem") ||
            lower.contains("refund") || lower.contains("return") || lower.contains("exchange") ||
            lower.contains("policy") || lower.contains("notice") || lower.contains("important") ||
            lower.contains("discrepancy") || lower.contains("goods") || lower.contains("within") ||
            lower.contains("days") || lower.contains("condition") || lower.contains("original") ||
            lower.contains("invoice") || lower.contains("receipt") || lower.contains("bill") ||
            lower.contains("uom") || lower.contains("qty") || lower.contains("price") ||
            lower.contains("rate") || lower.contains("amount") || lower.contains("ln item") ||
            lower.contains("description")
        ) {
            return null
        }

        // 4. Skip lines with timestamps (12:30), phone numbers (011...), or symbols
        if (trimmed.contains(Regex("""\d{1,2}:\d{2}"""))) return null
        if (trimmed.contains(Regex("""011\d{1,2}\s*\d{3}"""))) return null
        if (trimmed.startsWith("**") || trimmed.startsWith("(c)") || trimmed.startsWith("©") || trimmed.startsWith("//")) return null

        // 5. Clean leading punctuation/colons/symbols e.g. ":nipuna Samba Rice 1kg (50)" -> "Nipuna Samba Rice 1kg"
        var cleaned = trimmed
            .replace(Regex("""^[^a-zA-Z]+"""), "")                                     // Strip leading colons, bullets, slashes
            .replace(Regex("""(?i)\b(rs\.?|lkr|\$|€|£)\s*\d+([.,]\d{2})?\b"""), "")     // Strip currency + prices
            .replace(Regex("""\b\d{1,5}[.,]\d{2}\b"""), "")                            // Strip standalone prices like 1250.00
            .replace(Regex("""\s*\(\s*\d+\s*\)\s*"""), " ")                            // Strip trailing parenthesized codes like (50)
            .replace(Regex("""[^a-zA-Z0-9)\]]+$"""), "")                              // Strip trailing symbols
            .replace(Regex("""\s+"""), " ")                                            // Collapse extra spaces
            .trim()

        val letterCount = cleaned.count { it.isLetter() }
        // Must contain at least 3 letters and reasonable length
        if (cleaned.length in 3..40 && letterCount >= 3) {
            // Capitalize nicely: "nipuna samba rice 1kg" -> "Nipuna Samba Rice 1kg"
            return cleaned.split(" ").joinToString(" ") { word ->
                if (word.matches(Regex("""\d+(kg|g|l|ml|oz|lb)""", RegexOption.IGNORE_CASE))) {
                    word.lowercase(java.util.Locale.US)
                } else {
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
                }
            }
        }

        return null
    }

    private fun returnResult(selectedItem: String, allItems: List<String>) {
        val resultIntent = Intent().apply {
            putExtra("RECEIPT_ITEM_NAME", selectedItem)
            putStringArrayListExtra("RECEIPT_ALL_ITEMS", ArrayList(allItems))
        }
        setResult(RESULT_OK, resultIntent)
        Toast.makeText(this, "Selected: $selectedItem", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val plane = imageProxy.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun showLoading(show: Boolean) {
        progressOverlay.visibility = if (show) View.VISIBLE else View.GONE
        btnCaptureReceipt.isEnabled = !show
        btnPickGallery.isEnabled = !show
        btnDemoReceipt.isEnabled = !show
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}
