package com.example.barcode_sdk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.barcode_sdk.databinding.ActivityMainBinding
import com.example.scanner_sdk.customview.single.ScannerController
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scannerController: ScannerController

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSingleScanner()
        else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.navSingleScan.visibility = View.VISIBLE

        // Request camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startSingleScanner()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        binding.navSingleScan.setOnClickListener {
            binding.multiScannerView.visibility = View.GONE
            binding.authScannerView.visibility = View.GONE
            startSingleScanner()
        }

        binding.navMultiScan.setOnClickListener {
            binding.singleScannerView.visibility = View.GONE
            binding.authScannerView.visibility = View.GONE
            startMultiScanner()
        }

        binding.navAuthentication.setOnClickListener {
            binding.multiScannerView.visibility = View.GONE
            binding.singleScannerView.visibility = View.GONE
            startAuthScanner()
        }
    }

    private fun startSingleScanner() {
        Log.d("SCANNER", "Has camera permission: ${
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        }")
        scannerController = ScannerController(
            singleScannerView = binding.singleScannerView,
            lifecycleOwner = this
        ) { scannedValue ->
            // This callback triggers when a barcode is scanned
            Toast.makeText(this, "Scanned: $scannedValue", Toast.LENGTH_SHORT).show()
        }

        scannerController.startSingleScanner(this)
    }
    private fun startMultiScanner() {
        Log.d("SCANNER", "Has camera permission: ${
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        }")
        scannerController = ScannerController(
            multiScannerView = binding.multiScannerView,
            lifecycleOwner = this
        ) { scannedValue ->
            // This callback triggers when a barcode is scanned
            Toast.makeText(this, "Scanned: $scannedValue", Toast.LENGTH_SHORT).show()
        }

        scannerController.startMultiScanner(this)
    }

    private fun startAuthScanner() {
        Log.d("SCANNER", "Has camera permission: ${
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        }")
        scannerController = ScannerController(
            authScannerView = binding.authScannerView,
            lifecycleOwner = this
        ) { scannedValue ->
            // This callback triggers when a barcode is scanned
            Toast.makeText(this, "Scanned: $scannedValue", Toast.LENGTH_SHORT).show()
        }

        scannerController.startAuthScanner(this)
    }
}
