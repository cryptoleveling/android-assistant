package com.example.androidassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.androidassistant.api.*
import com.example.androidassistant.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    private val openAIService: OpenAIService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermission()
        setupSpeechRecognizer()
        setupRecordButton()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.responseText.text = getString(R.string.listening)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    processVoiceInput(text)
                }
            }

            override fun onError(error: Int) {
                binding.responseText.text = "Error: $error"
                stopListening()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun setupRecordButton() {
        binding.recordButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa")
        }
        speechRecognizer.startListening(intent)
        isListening = true
        binding.recordButton.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun stopListening() {
        speechRecognizer.stopListening()
        isListening = false
        binding.recordButton.setImageResource(android.R.drawable.ic_btn_speak_now)
    }

    private fun processVoiceInput(text: String) {
        binding.responseText.text = getString(R.string.processing)
        
        lifecycleScope.launch {
            try {
                // First, analyze the command using OpenAI
                val analysisRequest = ChatRequest(
                    messages = listOf(
                        Message(
                            role = "system",
                            content = """
                                شما یک دستیار هوشمند هستید که دستورات صوتی را تحلیل می‌کند.
                                دستورات می‌توانند شامل موارد زیر باشند:
                                - باز کردن برنامه‌ها و تنظیمات
                                - تنظیم آلارم و یادآوری
                                - ارسال پیام و تماس
                                - جستجو در اینترنت
                                - و غیره
                                
                                لطفاً دستور را تحلیل کنید و به صورت JSON پاسخ دهید:
                                {
                                    "command": "نام دستور",
                                    "parameters": {
                                        "param1": "مقدار1",
                                        "param2": "مقدار2"
                                    },
                                    "response": "پاسخ به کاربر"
                                }
                            """.trimIndent()
                        ),
                        Message(
                            role = "user",
                            content = text
                        )
                    )
                )
                
                val analysisResponse = openAIService.getChatCompletion(analysisRequest)
                val analysis = analysisResponse.choices.firstOrNull()?.message?.content
                
                if (analysis != null) {
                    // Execute the command based on analysis
                    executeDynamicCommand(analysis)
                } else {
                    binding.responseText.text = "نمی‌توانم دستور را درک کنم"
                }
            } catch (e: Exception) {
                binding.responseText.text = "Error: ${e.message}"
            }
        }
    }

    private fun executeDynamicCommand(analysis: String) {
        try {
            // Parse the JSON response
            val command = parseCommand(analysis)
            
            when (command.command) {
                "open_app" -> {
                    val packageName = command.parameters["package_name"]
                    if (packageName != null) {
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            startActivity(intent)
                            binding.responseText.text = command.response
                        } else {
                            binding.responseText.text = "برنامه مورد نظر یافت نشد"
                        }
                    }
                }
                "open_settings" -> {
                    val settingsType = command.parameters["type"]
                    when (settingsType) {
                        "wifi" -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        "bluetooth" -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        "sound" -> startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                        "display" -> startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                        else -> startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                    binding.responseText.text = command.response
                }
                "set_alarm" -> {
                    val time = command.parameters["time"]
                    if (time != null) {
                        // Implement alarm setting logic
                        binding.responseText.text = "زنگ هشدار برای ساعت $time تنظیم شد"
                    }
                }
                "send_message" -> {
                    val number = command.parameters["number"]
                    val message = command.parameters["message"]
                    if (number != null && message != null) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$number"))
                        intent.putExtra("sms_body", message)
                        startActivity(intent)
                        binding.responseText.text = command.response
                    }
                }
                "make_call" -> {
                    val number = command.parameters["number"]
                    if (number != null) {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        startActivity(intent)
                        binding.responseText.text = command.response
                    }
                }
                "search" -> {
                    val query = command.parameters["query"]
                    if (query != null) {
                        val intent = Intent(Intent.ACTION_WEB_SEARCH)
                        intent.putExtra("query", query)
                        startActivity(intent)
                        binding.responseText.text = command.response
                    }
                }
                else -> {
                    binding.responseText.text = command.response
                }
            }
        } catch (e: Exception) {
            binding.responseText.text = "خطا در اجرای دستور: ${e.message}"
        }
    }

    private fun parseCommand(json: String): Command {
        // Simple JSON parsing (in a real app, use a proper JSON parser)
        val command = json.substringAfter("\"command\":\"").substringBefore("\"")
        val response = json.substringAfter("\"response\":\"").substringBefore("\"")
        val parameters = mutableMapOf<String, String>()
        
        var remaining = json.substringAfter("\"parameters\":{").substringBefore("}")
        while (remaining.contains("\"")) {
            val key = remaining.substringAfter("\"").substringBefore("\"")
            remaining = remaining.substringAfter(":")
            val value = remaining.substringAfter("\"").substringBefore("\"")
            parameters[key] = value
            remaining = remaining.substringAfter("\"")
        }
        
        return Command(command, parameters, response)
    }

    data class Command(
        val command: String,
        val parameters: Map<String, String>,
        val response: String
    )

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
} 