package com.example.myapp

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize ApiKeyManager and save OpenAI API key
        ApiKeyManager.initialize("YOUR_OPENAI_API_KEY")
    }
}