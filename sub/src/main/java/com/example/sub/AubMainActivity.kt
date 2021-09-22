package com.example.sub

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

@Trace
class AubMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }
}