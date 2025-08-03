package com.application.inventoryapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var nameText: TextView
    private lateinit var skuText: TextView
    private lateinit var descText: TextView
    private lateinit var totalText: TextView
    private lateinit var ofText: TextView
    private lateinit var ibText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this) // Init Firebase

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Bind UI
        nameText = findViewById(R.id.nameText)
        skuText = findViewById(R.id.skuText)
        descText = findViewById(R.id.descText)
        totalText = findViewById(R.id.totalText)
        ofText = findViewById(R.id.ofText)
        ibText = findViewById(R.id.ibText)

        val scanButton = findViewById<Button>(R.id.button)
        scanButton.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            startActivityForResult(intent, 1002)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1002 && resultCode == RESULT_OK) {
            val barcode = data?.getStringExtra("barcode")
            if (barcode != null) {
                // Remove any simple Toast here so no quick "name" prompt
                // Toast.makeText(this, "Scanned: $barcode", Toast.LENGTH_LONG).show()

                fetchItemFromFirebase(barcode)  // This handles everything now
            } else {
                Toast.makeText(this, "No barcode received", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun fetchItemFromFirebase(barcode: String) {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("scanned_items").child(barcode)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "N/A"
                    val sku = snapshot.child("sku").getValue(String::class.java) ?: "N/A"
                    val desc = snapshot.child("desc").getValue(String::class.java) ?: "N/A"
                    val totalStock = snapshot.child("totalStock").getValue(Int::class.java) ?: 0
                    val onFloor = snapshot.child("onFloor").getValue(Int::class.java) ?: 0
                    val inBack = snapshot.child("inBack").getValue(Int::class.java) ?: 0

                    updateUI(name, sku, desc, totalStock, onFloor, inBack)
                } else {
                    showNewItemDialog(barcode)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Firebase error: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Firebase error", error.toException())
            }
        })
    }

    private fun updateUI(
        name: String,
        sku: String,
        desc: String,
        totalStock: Int,
        onFloor: Int,
        inBack: Int
    ) {
        nameText.text = name
        skuText.text = "SKU: $sku"
        descText.text = "DESC: $desc"
        totalText.text = "Total stock: $totalStock"
        ofText.text = "On floor: $onFloor"
        ibText.text = "In back: $inBack"
    }

    private fun showNewItemDialog(barcode: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("New item not found")
        builder.setMessage("Please enter details for this item")

        val viewInflated = layoutInflater.inflate(R.layout.dialog_new_item, null)
        val inputName = viewInflated.findViewById<EditText>(R.id.inputName)
        val inputSku = viewInflated.findViewById<EditText>(R.id.inputSku)
        val inputDesc = viewInflated.findViewById<EditText>(R.id.inputDesc)
        val inputTotal = viewInflated.findViewById<EditText>(R.id.inputTotal)
        val inputOnFloor = viewInflated.findViewById<EditText>(R.id.inputOnFloor)
        val inputInBack = viewInflated.findViewById<EditText>(R.id.inputInBack)

        builder.setView(viewInflated)

        builder.setPositiveButton("Save") { dialog, _ ->
            val name = inputName.text.toString()
            val sku = inputSku.text.toString()
            val desc = inputDesc.text.toString()
            val totalStock = inputTotal.text.toString().toIntOrNull() ?: 0
            val onFloor = inputOnFloor.text.toString().toIntOrNull() ?: 0
            val inBack = inputInBack.text.toString().toIntOrNull() ?: 0

            saveNewItemToFirebase(barcode, name, sku, desc, totalStock, onFloor, inBack)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun saveNewItemToFirebase(
        barcode: String,
        name: String,
        sku: String,
        desc: String,
        totalStock: Int,
        onFloor: Int,
        inBack: Int
    ) {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("scanned_items").child(barcode)

        val newItem = mapOf(
            "name" to name,
            "sku" to sku,
            "desc" to desc,
            "totalStock" to totalStock,
            "onFloor" to onFloor,
            "inBack" to inBack
        )

        ref.setValue(newItem)
            .addOnSuccessListener {
                Toast.makeText(this, "Item saved!", Toast.LENGTH_SHORT).show()
                updateUI(name, sku, desc, totalStock, onFloor, inBack)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving item: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
