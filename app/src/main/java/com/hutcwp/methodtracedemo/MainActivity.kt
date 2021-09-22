package com.hutcwp.methodtracedemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Trace
import java.lang.Exception

@com.example.sub.Trace
class MainActivity : AppCompatActivity() {

    private var a = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }

    fun test() {
        if (a) {
            return
        }

        val b = 2
        return
    }

    fun testCatch() {
        try {
            throw Exception("null")
        } catch (e: Exception) {

        }
    }
}