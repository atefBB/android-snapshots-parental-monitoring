package com.parentalmonitor.parental_monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ScreenshotService : Service() {

    companion object {
        const val ACTION_START = "com.parentalmonitor.action.START"
        const val ACTION_STOP = "com.parentalmonitor.action.STOP"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_DATA = "DATA"
        const val EXTRA_INTERVAL_MS = "INTERVAL_MS"
        const val EXTRA_SMTP_HOST = "SMTP_HOST"
        const val EXTRA_SMTP_PORT = "SMTP_PORT"
        const val EXTRA_SMTP_USERNAME = "SMTP_USERNAME"
        const val EXTRA_SMTP_PASSWORD = "SMTP_PASSWORD"
        const val EXTRA_RECIPIENT = "RECIPIENT"
        const val EXTRA_FROM_EMAIL = "FROM_EMAIL"
        const val CHANNEL_ID = "screenshot_service_channel"
        const val NOTIFICATION_ID = 1001

        private const val SCREENSHOTS_DIR = "screenshots"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var captureRunnable: Runnable? = null

    // Shared executor for background tasks (email sending)
    private val emailExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Email settings
    private var smtpHost = ""
    private var smtpPort = 587
    private var smtpUsername = ""
    private var smtpPassword = ""
    private var recipientEmail = ""
    private var fromEmail = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        backgroundHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, 60000L)

                // Read email settings
                smtpHost = intent.getStringExtra(EXTRA_SMTP_HOST) ?: ""
                smtpPort = intent.getIntExtra(EXTRA_SMTP_PORT, 587)
                smtpUsername = intent.getStringExtra(EXTRA_SMTP_USERNAME) ?: ""
                smtpPassword = intent.getStringExtra(EXTRA_SMTP_PASSWORD) ?: ""
                recipientEmail = intent.getStringExtra(EXTRA_RECIPIENT) ?: ""
                fromEmail = intent.getStringExtra(EXTRA_FROM_EMAIL) ?: smtpUsername

                if (resultCode == -1 || data == null) {
                    Toast.makeText(this, "Failed to start: invalid permission data", Toast.LENGTH_LONG).show()
                    stopSelf()
                    return START_NOT_STICKY
                }

                val notification = buildNotification()
                startForeground(NOTIFICATION_ID, notification)

                try {
                    setupMediaProjection(resultCode, data)
                    setupVirtualDisplay()
                    startPeriodicCapture(intervalMs)
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to start capture: ${e.message}", Toast.LENGTH_LONG).show()
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        emailExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for the background screen monitoring service"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Parental Monitor Active")
            .setContentText("Monitoring screen activity")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, null)
    }

    private fun setupVirtualDisplay() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        val display = windowManager.defaultDisplay
        display.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // Create ImageReader with 2 max images for queue depth
        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun startPeriodicCapture(intervalMs: Long) {
        captureRunnable = Runnable {
            try {
                val filePath = captureScreenshot()
                if (filePath != null) {
                    sendEmailWithScreenshot(filePath)
                }
            } catch (e: Exception) {
                // Log error but continue; will retry on next interval
            }
            // Use the captured Runnable for rescheduling
            val self = captureRunnable ?: return@Runnable
            backgroundHandler?.postDelayed(self, intervalMs)
        }
        // First capture after a short delay to allow the virtual display to initialize
        val runnable = captureRunnable ?: return
        backgroundHandler?.postDelayed(runnable, 2000)
    }

    private fun captureScreenshot(): String? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null

        try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Handle potential row stride differences (common on many devices)
            if (rowStride == width * pixelStride) {
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)
            } else {
                // Copy row-by-row accounting for stride padding
                val pixels = IntArray(width * height)
                buffer.rewind()
                buffer.order(java.nio.ByteOrder.nativeOrder())

                for (row in 0 until height) {
                    buffer.position(row * rowStride)
                    for (col in 0 until width) {
                        val r = buffer.get().toInt() and 0xFF
                        val g = buffer.get().toInt() and 0xFF
                        val b = buffer.get().toInt() and 0xFF
                        val a = buffer.get().toInt() and 0xFF
                        pixels[row * width + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            }

            // Save to file
            val screenshotsDir = File(filesDir, SCREENSHOTS_DIR)
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs()
            }

            val timestamp = System.currentTimeMillis()
            val screenshotFile = File(screenshotsDir, "screenshot_$timestamp.png")

            FileOutputStream(screenshotFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            }

            bitmap.recycle()
            return screenshotFile.absolutePath
        } catch (e: Exception) {
            return null
        } finally {
            image.close()
        }
    }

    private fun sendEmailWithScreenshot(filePath: String) {
        if (smtpHost.isEmpty() || smtpUsername.isEmpty() || smtpPassword.isEmpty() || recipientEmail.isEmpty()) {
            return
        }

        emailExecutor.submit {
            try {
                val props = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.host", smtpHost)
                    put("mail.smtp.port", smtpPort.toString())
                    put("mail.smtp.ssl.trust", smtpHost)
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(smtpUsername, smtpPassword)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(fromEmail))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail))
                    subject = "Parental Monitor - Screenshot (${System.currentTimeMillis()})"

                    // Email body
                    val textPart = MimeBodyPart()
                    textPart.setText("Screenshot captured at ${java.util.Date().toString()}.\n\nThis is an automated message from Parental Monitor.")

                    // Attachment
                    val filePart = MimeBodyPart().apply {
                        val source = FileDataSource(File(filePath))
                        dataHandler = DataHandler(source)
                        setFileName("screenshot_${System.currentTimeMillis()}.png")
                    }

                    // Combine parts
                    val multipart = MimeMultipart()
                    multipart.addBodyPart(textPart)
                    multipart.addBodyPart(filePart)
                    setContent(multipart)
                }

                Transport.send(message)
            } catch (e: Exception) {
                // Silently fail — will retry on next capture interval
            }
        }
    }

    private fun stopCapture() {
        backgroundHandler?.removeCallbacksAndMessages(null)
        captureRunnable = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}

        try {
            imageReader?.close()
        } catch (_: Exception) {}

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}

        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
