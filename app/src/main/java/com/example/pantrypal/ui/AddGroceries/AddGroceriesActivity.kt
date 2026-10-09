
package com.example.pantrypal.ui.AddGroceries

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pantrypal.MainActivity
import com.example.pantrypal.R
import com.example.pantrypal.data.model.PantryItem
import com.example.pantrypal.data.remote.SupabaseProvider
import com.example.pantrypal.data.repository.PantryRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddGroceriesActivity : AppCompatActivity() {

    private var scannedBarcode: String? = null
    private var quantity: Int = 1

    // Quantity UI refs
    private lateinit var tvQuantityCount: TextView
    private lateinit var btnQuantityMinus: TextView
    private lateinit var btnQuantityPlus: TextView

    // Form refs
    private lateinit var etItemName: EditText
    private lateinit var tvCategory: TextView
    private lateinit var tvExpiryDate: TextView
    private lateinit var tvStorageZone: TextView
    private lateinit var btnConfirmAdd: TextView

    // Scroll / layout refs
    private lateinit var scrollViewMain: ScrollView
    private lateinit var cardPreFilledEntry: LinearLayout

    // Product info refs
    private lateinit var tvDetectedProductName: TextView
    private lateinit var tvDetectedProductDetails: TextView

    private val categories = listOf(
        "Dairy",
        "Vegetables",
        "Fruits",
        "Grains",
        "Canned Goods",
        "Snacks"
    )

    private val storageZones = listOf(
        "Fridge - Shelf 1", "Fridge - Shelf 2", "Fridge - Door",
        "Crisper Drawer", "Freezer", "Pantry Shelf A",
        "Pantry Shelf B", "Cabinet 1", "Cabinet 2", "Counter"
    )

    private val barcodeScannerLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
                if (!barcode.isNullOrBlank()) {
                    scannedBarcode = barcode
                    lookupProduct(barcode)
                } else {
                    Toast.makeText(this, "Barcode result is empty", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val receiptOcrLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val itemName = result.data?.getStringExtra("RECEIPT_ITEM_NAME")
                val allItems = result.data?.getStringArrayListExtra("RECEIPT_ALL_ITEMS")

                if (!itemName.isNullOrBlank()) {
                    scannedBarcode = null
                    etItemName.setText(itemName)
                    tvDetectedProductName.text = itemName
                    val countText = if ((allItems?.size ?: 0) > 1) " • 1 of ${allItems!!.size} detected" else ""
                    tvDetectedProductDetails.text = "Recognized from Receipt$countText"

                    // Auto-categorize based on common food names
                    val lower = itemName.lowercase()
                    val daysToAdd: Int
                    when {
                        lower.contains("milk") || lower.contains("cheese") || lower.contains("yogurt") ||
                        lower.contains("butter") || lower.contains("curd") || lower.contains("cream") ||
                        lower.contains("paneer") -> {
                            tvCategory.text = "Dairy"
                            tvStorageZone.text = "Fridge - Shelf 1"
                            daysToAdd = 7
                        }
                        lower.contains("spinach") || lower.contains("tomato") || lower.contains("onion") ||
                        lower.contains("potato") || lower.contains("carrot") || lower.contains("salad") ||
                        lower.contains("leek") || lower.contains("cabbage") || lower.contains("bean") ||
                        lower.contains("garlic") || lower.contains("ginger") || lower.contains("chili") ||
                        lower.contains("pumpkin") -> {
                            tvCategory.text = "Vegetables"
                            tvStorageZone.text = "Crisper Drawer"
                            daysToAdd = 5
                        }
                        lower.contains("apple") || lower.contains("banana") || lower.contains("berry") ||
                        lower.contains("orange") || lower.contains("strawberry") || lower.contains("mango") ||
                        lower.contains("grape") || lower.contains("papaya") || lower.contains("lemon") ||
                        lower.contains("lime") || lower.contains("melon") -> {
                            tvCategory.text = "Fruits"
                            tvStorageZone.text = "Crisper Drawer"
                            daysToAdd = 5
                        }
                        lower.contains("bread") || lower.contains("oat") || lower.contains("rice") ||
                        lower.contains("flour") || lower.contains("pasta") || lower.contains("noodle") ||
                        lower.contains("samba") || lower.contains("nadu") || lower.contains("basmati") ||
                        lower.contains("keeri") || lower.contains("dhal") || lower.contains("dahl") ||
                        lower.contains("cereal") || lower.contains("grain") -> {
                            tvCategory.text = "Grains"
                            tvStorageZone.text = "Pantry Shelf A"
                            daysToAdd = 90
                        }
                        lower.contains("can") || lower.contains("soup") || lower.contains("tuna") ||
                        lower.contains("sardine") || lower.contains("tin") || lower.contains("salmon") ||
                        lower.contains("mackerel") || lower.contains("fish") -> {
                            tvCategory.text = "Canned Goods"
                            tvStorageZone.text = "Cabinet 1"
                            daysToAdd = 180
                        }
                        lower.contains("chip") || lower.contains("snack") || lower.contains("cookie") ||
                        lower.contains("cracker") || lower.contains("chocolate") || lower.contains("biscuit") ||
                        lower.contains("wafer") || lower.contains("cake") || lower.contains("sugar") ||
                        lower.contains("tea") || lower.contains("coffee") -> {
                            tvCategory.text = "Snacks"
                            tvStorageZone.text = "Pantry Shelf B"
                            daysToAdd = 60
                        }
                        else -> {
                            tvCategory.text = "Grains"
                            tvStorageZone.text = "Pantry Shelf A"
                            daysToAdd = 60
                        }
                    }

                    // Auto-fill expiry date with calculated date
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_MONTH, daysToAdd)
                    val dateFmt = SimpleDateFormat("MM/dd/yyyy", Locale.US)
                    tvExpiryDate.text = dateFmt.format(cal.time)

                    Toast.makeText(this, "Receipt item: $itemName", Toast.LENGTH_SHORT).show()

                    // Scroll down to the form
                    cardPreFilledEntry.post {
                        scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_groceries)

        // Bind views
        tvQuantityCount   = findViewById(R.id.tvQuantityCount)
        btnQuantityMinus  = findViewById(R.id.btnQuantityMinus)
        btnQuantityPlus   = findViewById(R.id.btnQuantityPlus)
        etItemName        = findViewById(R.id.tvItemName)
        tvCategory        = findViewById(R.id.tvCategory)
        tvExpiryDate      = findViewById(R.id.tvExpiryDate)
        tvStorageZone     = findViewById(R.id.tvStorageZone)
        btnConfirmAdd     = findViewById(R.id.btnConfirmAdd)
        tvDetectedProductName    = findViewById(R.id.tvDetectedProductName)
        tvDetectedProductDetails = findViewById(R.id.tvDetectedProductDetails)
        scrollViewMain    = findViewById(R.id.scrollViewMain)
        cardPreFilledEntry = findViewById(R.id.cardPreFilledEntry)

        tvDetectedProductName.text = "No product scanned yet"
        tvDetectedProductDetails.text = "Scan barcode or tap Manual Entry"

        // Scan barcode card
        findViewById<LinearLayout>(R.id.cardScanBarcode).setOnClickListener {
            barcodeScannerLauncher.launch(Intent(this, BarcodeScannerActivity::class.java))
        }

        // Receipt OCR card
        findViewById<LinearLayout>(R.id.cardReceiptOcr).setOnClickListener {
            receiptOcrLauncher.launch(Intent(this, ReceiptScannerActivity::class.java))
        }

        // Manual Entry card → clear product info and scroll to form
        findViewById<LinearLayout>(R.id.cardManualEntry).setOnClickListener {
            // Clear any previously scanned product data
            scannedBarcode = null
            etItemName.setText("")
            etItemName.requestFocus()
            tvDetectedProductName.text = "Enter details below"
            tvDetectedProductDetails.text = ""

            // Smooth scroll to the pre-filled entry card
            cardPreFilledEntry.post {
                scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top)
            }
        }

        // Quantity stepper
        btnQuantityMinus.setOnClickListener {
            if (quantity > 1) {
                quantity--
                tvQuantityCount.text = quantity.toString()
            }
        }
        btnQuantityPlus.setOnClickListener {
            quantity++
            tvQuantityCount.text = quantity.toString()
        }

        // Category picker
        tvCategory.setOnClickListener { showCategoryPicker() }

        // Storage zone picker
        tvStorageZone.setOnClickListener { showStorageZonePicker() }

        // Expiry date picker
        tvExpiryDate.setOnClickListener { showDatePicker() }

        // Confirm & Add
        btnConfirmAdd.setOnClickListener { saveGroceryItem() }

        // ── Bottom Nav Bar ────────────────────────────────────────────────────
        findViewById<LinearLayout>(R.id.navPantry).setOnClickListener {
            navigateToMain("pantry")
        }
        findViewById<LinearLayout>(R.id.navAdd).setOnClickListener {
            // Already on Add screen — do nothing or scroll to top
            scrollViewMain.smoothScrollTo(0, 0)
        }
        findViewById<LinearLayout>(R.id.navShopping).setOnClickListener {
            navigateToMain("shopping")
        }
        findViewById<LinearLayout>(R.id.navSurplus).setOnClickListener {
            navigateToMain("surplus")
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            navigateToMain("profile")
        }
    }

    private fun navigateToMain(tab: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", tab)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    // ── Pickers ──────────────────────────────────────────────────────────────

    private fun showCategoryPicker() {
        AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setItems(categories.toTypedArray()) { _, which ->
                tvCategory.text = categories[which]
            }
            .show()
    }

    private fun showStorageZonePicker() {
        AlertDialog.Builder(this)
            .setTitle("Select Storage Zone")
            .setItems(storageZones.toTypedArray()) { _, which ->
                tvStorageZone.text = storageZones[which]
            }
            .show()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()

        // Pre-populate picker from existing text if valid
        runCatching {
            val fmt = SimpleDateFormat("MM/dd/yyyy", Locale.US)
            fmt.isLenient = false
            val d = fmt.parse(tvExpiryDate.text.toString())
            if (d != null) cal.time = d
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                tvExpiryDate.text = String.format(Locale.US, "%02d/%02d/%04d", month + 1, day, year)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ── Product lookup (OpenFoodFacts + QR parser + Local samples) ────────────

    private val sampleProducts = mapOf(
        "3017620422003" to Pair("Nutella Hazelnut Spread", "Ferrero • 400g"),
        "5449000000996" to Pair("Coca-Cola Original", "Coca-Cola • 330ml"),
        "737628064502"  to Pair("Thai Kitchen Coconut Milk", "Thai Kitchen • 403ml"),
        "011110038364"  to Pair("Kroger Whole Milk", "Kroger • 1 Gallon"),
        "8901030383786" to Pair("Lipton Green Tea", "Lipton • 100g")
    )

    private fun lookupProduct(rawBarcode: String) {
        val barcode = rawBarcode.trim()

        // 1. If scanned value is JSON (e.g. from custom QR code)
        if (barcode.startsWith("{") && barcode.endsWith("}")) {
            runCatching {
                val obj = JSONObject(barcode)
                val name = obj.optString("item_name", obj.optString("name", obj.optString("title", "")))
                val brand = obj.optString("brand", "")
                val category = obj.optString("category", "")
                if (name.isNotBlank()) {
                    tvDetectedProductName.text = name
                    etItemName.setText(name)
                    if (category.isNotBlank()) tvCategory.text = category
                    tvDetectedProductDetails.text = brand.ifBlank { "Scanned from QR code" }
                    Toast.makeText(this, "QR product parsed: $name", Toast.LENGTH_SHORT).show()
                    cardPreFilledEntry.post { scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top) }
                    return
                }
            }
        }

        // 2. If scanned value is a web URL
        if (barcode.startsWith("http://") || barcode.startsWith("https://")) {
            val lastSegment = barcode.substringAfterLast("/").substringBefore("?").replace("-", " ").replace("_", " ")
            val guessedName = if (lastSegment.length in 3..35 && !lastSegment.contains(".")) {
                lastSegment.replaceFirstChar { it.uppercase() }
            } else {
                "Scanned Item"
            }
            tvDetectedProductName.text = guessedName
            etItemName.setText(if (guessedName != "Scanned Item") guessedName else "")
            tvDetectedProductDetails.text = "URL: ${barcode.take(32)}..."
            Toast.makeText(this, "Scanned QR code", Toast.LENGTH_SHORT).show()
            cardPreFilledEntry.post { scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top) }
            return
        }

        // 3. If scanned value is text (e.g. user created QR code saying "Banana" or "Milk")
        if (barcode.any { it.isLetter() }) {
            tvDetectedProductName.text = barcode
            etItemName.setText(barcode)
            tvDetectedProductDetails.text = "Scanned from QR code"
            Toast.makeText(this, "Product detected: $barcode", Toast.LENGTH_SHORT).show()
            cardPreFilledEntry.post { scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top) }
            return
        }

        // 4. Check offline sample dictionary
        sampleProducts[barcode]?.let { (name, details) ->
            tvDetectedProductName.text = name
            etItemName.setText(name)
            tvDetectedProductDetails.text = details
            Toast.makeText(this, "Product found: $name", Toast.LENGTH_SHORT).show()
            cardPreFilledEntry.post { scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top) }
            return
        }

        // 5. Query OpenFoodFacts API for numeric barcodes
        Toast.makeText(this, "Looking up barcode $barcode...", Toast.LENGTH_SHORT).show()

        Thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout    = 10_000
                connection.setRequestProperty("User-Agent", "PantryPal-Android/1.0")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)

                    if (json.optInt("status", 0) == 1) {
                        val product = json.optJSONObject("product")

                        val productName = product?.optString("product_name", "")?.trim().orEmpty()
                        val brand       = product?.optString("brands",       "")?.trim().orEmpty()
                        val qty         = product?.optString("quantity",      "")?.trim().orEmpty()

                        runOnUiThread {
                            if (productName.isNotBlank()) {
                                tvDetectedProductName.text = productName
                                etItemName.setText(productName)

                                val details = listOf(brand, qty)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                tvDetectedProductDetails.text = details.ifBlank { "Barcode: $barcode" }

                                Toast.makeText(this, "Product found!", Toast.LENGTH_SHORT).show()

                                cardPreFilledEntry.post {
                                    scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top)
                                }
                            } else {
                                showProductNotFound(barcode)
                            }
                        }
                    } else {
                        runOnUiThread { showProductNotFound(barcode) }
                    }
                } else {
                    runOnUiThread { showProductNotFound(barcode) }
                }
            } catch (e: Exception) {
                runOnUiThread { showLookupError(barcode) }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    // ── Save to Supabase ─────────────────────────────────────────────────────

    private fun saveGroceryItem() {
        val itemName = etItemName.text.toString().trim()

        if (itemName.isBlank()) {
            Toast.makeText(this, "Please enter an item name", Toast.LENGTH_SHORT).show()
            return
        }

        val category    = tvCategory.text.toString().trim()
        val storageZone = tvStorageZone.text.toString().trim()
        val expiryText  = tvExpiryDate.text.toString().trim()

        val formattedExpiry = runCatching {
            val inputFmt = SimpleDateFormat("MM/dd/yyyy", Locale.US).apply { isLenient = false }
            val outputFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            outputFmt.format(inputFmt.parse(expiryText)!!)
        }.getOrNull()

        val barcodeValue = if (!scannedBarcode.isNullOrBlank()) {
            scannedBarcode!!
        } else {
            "N/A"
        }

        val payload = buildJsonObject {
            put("item_name",    itemName)
            put("quantity",     quantity.toDouble())
            put("category",     category)
            put("storage_zone", storageZone)
            put("barcode",      barcodeValue)
            formattedExpiry?.let  { put("expiry_date", it) }
        }

        btnConfirmAdd.isEnabled = false

        lifecycleScope.launch {
            try {
                SupabaseProvider.client
                    .from("add_groceries_items")
                    .insert(payload)

                // Add to PantryRepository so it shows on the Pantry dashboard immediately
                val daysUntilExpiry: Int = if (formattedExpiry != null) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                        val expiry = sdf.parse(formattedExpiry)
                        if (expiry != null) {
                            val diff = expiry.time - System.currentTimeMillis()
                            (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                        } else {
                            30
                        }
                    } catch (e: Exception) {
                        30
                    }
                } else {
                    30
                }

                val expiryLabel = when {
                    daysUntilExpiry == 0 -> "Expires: Today"
                    daysUntilExpiry == 1 -> "Expires: Tomorrow"
                    daysUntilExpiry <= 3 -> "Expires: In $daysUntilExpiry days"
                    else -> "Expires in $daysUntilExpiry days"
                }

                val iconResId = when (category.lowercase()) {
                    "dairy" -> R.drawable.ic_dairy
                    "vegetables" -> R.drawable.ic_vegetable
                    "fruits" -> R.drawable.ic_fruit
                    "grains" -> R.drawable.ic_food_bread
                    "canned goods" -> R.drawable.ic_canned
                    "snacks" -> R.drawable.ic_snack
                    else -> R.drawable.ic_pantry
                }

                PantryRepository.addDynamicItem(
                    PantryItem(
                        id = UUID.randomUUID().toString(),
                        title = itemName,
                        category = category,
                        location = "Zone: $storageZone · Qty: $quantity",
                        quantityText = "$quantity",
                        expiryText = expiryLabel,
                        daysUntilExpiry = daysUntilExpiry,
                        ownerName = "You",
                        iconResId = iconResId,
                        stockPercent = 100,
                        stockText = "100% (Full)",
                        isExpiringSoon = daysUntilExpiry <= 3,
                        progress = 1.0f
                    )
                )

                Toast.makeText(
                    this@AddGroceriesActivity,
                    "✓ $itemName added to Pantry!",
                    Toast.LENGTH_LONG
                ).show()

                // Navigate back to the Pantry screen
                navigateToMain("pantry")

            } catch (e: Exception) {
                android.util.Log.e("PantryPalSave", "Save failed", e)
                Toast.makeText(
                    this@AddGroceriesActivity,
                    "Save failed: ${e.message ?: "Check Supabase settings"}",
                    Toast.LENGTH_LONG
                ).show()
                btnConfirmAdd.isEnabled = true
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showProductNotFound(barcode: String) {
        tvDetectedProductName.text = "Unrecognized Code"
        tvDetectedProductDetails.text = "Code: $barcode (not in database)"
        etItemName.setText("")
        etItemName.requestFocus()
        Toast.makeText(
            this,
            "Item not in database. Enter details below.\nCode: $barcode",
            Toast.LENGTH_LONG
        ).show()
        cardPreFilledEntry.post {
            scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top)
        }
    }

    private fun showLookupError(barcode: String) {
        tvDetectedProductName.text = "Lookup Failed"
        tvDetectedProductDetails.text = "Code: $barcode (check internet)"
        etItemName.setText("")
        etItemName.requestFocus()
        Toast.makeText(
            this,
            "Could not connect to database. Enter details manually.",
            Toast.LENGTH_LONG
        ).show()
        cardPreFilledEntry.post {
            scrollViewMain.smoothScrollTo(0, cardPreFilledEntry.top)
        }
    }
}
