package com.openlotus.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.openlotus.app.log.AppLog
import com.openlotus.app.ui.OpenLotusApp
import com.openlotus.protocol.ProtocolLog

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d("app", "onCreate, sdk ${Build.VERSION.SDK_INT}")
        ProtocolLog.sink = { message -> AppLog.d("protocol", message) }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OpenLotusApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLog.d("app", "onStart")
    }

    override fun onStop() {
        AppLog.d("app", "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        AppLog.d("app", "onDestroy")
        super.onDestroy()
    }
}
