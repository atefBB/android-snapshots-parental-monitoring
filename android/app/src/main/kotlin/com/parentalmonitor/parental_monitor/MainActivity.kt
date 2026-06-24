package com.parentalmonitor.parental_monitor

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private companion object {
        private const val CHANNEL = "com.parentalmonitor/screenshot"
        private const val KEY_PENDING_CONFIG = "pending_service_config"
        private const val MEDIA_PROJECTION_REQUEST_CODE = 1001
    }

    private var methodChannelResult: MethodChannel.Result? = null
    private var pendingServiceConfig: ServiceConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore pending config if activity was recreated
        if (savedInstanceState != null) {
            pendingServiceConfig = savedInstanceState.getSerializable(KEY_PENDING_CONFIG) as? ServiceConfig
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingServiceConfig?.let {
            outState.putSerializable(KEY_PENDING_CONFIG, it)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            val config = pendingServiceConfig
            pendingServiceConfig = null

            if (resultCode == RESULT_OK && data != null) {
                if (config == null) {
                    methodChannelResult?.error("NO_CONFIG", "Missing service configuration", null)
                    methodChannelResult = null
                    return
                }

                val success = startScreenshotService(resultCode, data, config)
                methodChannelResult?.success(success)
                methodChannelResult = null
            } else {
                methodChannelResult?.error("PERMISSION_DENIED", "Screen capture permission was denied", null)
                methodChannelResult = null
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "isServiceRunning" -> {
                    result.success(isServiceRunning())
                }
                "stopService" -> {
                    stopScreenshotService()
                    result.success(true)
                }
                "requestStartService" -> {
                    val intervalMs = (call.argument<Int>("intervalMinutes") ?: 1) * 60 * 1000L
                    val smtpHost = call.argument<String>("smtpHost") ?: ""
                    val smtpPort = call.argument<Int>("smtpPort") ?: 587
                    val smtpUsername = call.argument<String>("smtpUsername") ?: ""
                    val smtpPassword = call.argument<String>("smtpPassword") ?: ""
                    val recipient = call.argument<String>("recipientEmail") ?: ""
                    val fromEmail = call.argument<String>("fromEmail") ?: smtpUsername

                    if (smtpHost.isEmpty() || smtpUsername.isEmpty() || smtpPassword.isEmpty() || recipient.isEmpty()) {
                        result.error("INVALID_CONFIG", "Please configure all email settings first", null)
                        return@setMethodCallHandler
                    }

                    methodChannelResult = result
                    pendingServiceConfig = ServiceConfig(
                        intervalMs = intervalMs,
                        smtpHost = smtpHost,
                        smtpPort = smtpPort,
                        smtpUsername = smtpUsername,
                        smtpPassword = smtpPassword,
                        recipient = recipient,
                        fromEmail = fromEmail
                    )
                    requestMediaProjectionPermission()
                }
                else -> result.notImplemented()
            }
        }
    }

    private data class ServiceConfig(
        val intervalMs: Long,
        val smtpHost: String,
        val smtpPort: Int,
        val smtpUsername: String,
        val smtpPassword: String,
        val recipient: String,
        val fromEmail: String
    ) : java.io.Serializable

    private fun requestMediaProjectionPermission() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        @Suppress("DEPRECATION")
        startActivityForResult(intent, MEDIA_PROJECTION_REQUEST_CODE)
    }

    private fun isServiceRunning(): Boolean {
        return ScreenshotServiceState.isRunning
    }

    private fun startScreenshotService(
        resultCode: Int,
        data: Intent,
        config: ServiceConfig
    ): Boolean {
        val intent = Intent(this, ScreenshotService::class.java).apply {
            action = ScreenshotService.ACTION_START
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenshotService.EXTRA_DATA, data)
            putExtra(ScreenshotService.EXTRA_INTERVAL_MS, config.intervalMs)
            putExtra(ScreenshotService.EXTRA_SMTP_HOST, config.smtpHost)
            putExtra(ScreenshotService.EXTRA_SMTP_PORT, config.smtpPort)
            putExtra(ScreenshotService.EXTRA_SMTP_USERNAME, config.smtpUsername)
            putExtra(ScreenshotService.EXTRA_SMTP_PASSWORD, config.smtpPassword)
            putExtra(ScreenshotService.EXTRA_RECIPIENT, config.recipient)
            putExtra(ScreenshotService.EXTRA_FROM_EMAIL, config.fromEmail)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }

        ScreenshotServiceState.isRunning = true
        return true
    }

    private fun stopScreenshotService() {
        val intent = Intent(this, ScreenshotService::class.java).apply {
            action = ScreenshotService.ACTION_STOP
        }
        startService(intent)
        ScreenshotServiceState.isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        methodChannelResult = null
    }
}

/**
 * Global state to track if the screenshot service is running.
 * Used by Flutter to check service status.
 */
object ScreenshotServiceState {
    var isRunning: Boolean = false
}
