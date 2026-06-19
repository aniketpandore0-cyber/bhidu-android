package com.bhidu.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bhidu.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceManager: VoiceManager
    private lateinit var geminiClient: GeminiClient
    private var isListening = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListeningFlow()
        } else {
            binding.statusText.text = "Mic permission chahiye, bhidu. Settings se on kar de."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val apiKey = "YOUR_GEMINI_API_KEY_HERE"
        geminiClient = GeminiClient(apiKey)

        voiceManager = VoiceManager(
            context = this,
            onSpeechResult = ::handleSpeechResult,
            onListeningStateChanged = ::handleListeningState,
            onSpeakingStateChanged = ::handleSpeakingState
        )
        voiceManager.init()

        binding.micButton.setOnClickListener {
            if (isListening) {
                voiceManager.stopListening()
            } else {
                checkPermissionAndListen()
            }
        }

        binding.statusText.text = "Tap kar aur bol, bhidu!"
    }

    private fun checkPermissionAndListen() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> startListeningFlow()
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningFlow() { voiceManager.startListening() }

    private fun handleListeningState(listening: Boolean) {
        isListening = listening
        runOnUiThread {
            binding.statusText.text = if (listening) "Sun raha hu..." else "Soch raha hu..."
            binding.micButton.alpha = if (listening) 1.0f else 0.6f
        }
    }

    private fun handleSpeakingState(speaking: Boolean) {
        runOnUiThread { if (speaking) binding.statusText.text = "Bol raha hu..." }
    }

    private fun handleSpeechResult(text: String) {
        if (text.isBlank()) {
            runOnUiThread { binding.statusText.text = "Kuch sunai nahi diya, bhidu. Phir bol." }
            return
        }
        runOnUiThread {
            binding.userText.text = text
            binding.statusText.text = "Soch raha hu..."
        }

        geminiClient.sendMessage(text, object : GeminiClient.Callback {
            override fun onResult(reply: String) {
                runOnUiThread {
                    binding.bhiduText.text = reply
                    binding.statusText.text = "Tap kar aur bol, bhidu!"
                }
                voiceManager.speak(reply)
            }
            override fun onError(message: String) {
                runOnUiThread {
                    binding.bhiduText.text = message
                    binding.statusText.text = "Tap kar aur bol, bhidu!"
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
    }
}
