package com.kaagazvault.camera

import android.graphics.ImageFormat
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import android.util.Size

/**
 * In-memory CameraX capture. The captured bitmap is encoded to bytes and handed to the
 * encrypted repository; this screen never writes a plaintext photo to a file or MediaStore.
 */
@Composable
internal fun CameraCaptureScreen(
    lifecycleOwner: LifecycleOwner,
    onCaptured: (ByteArray) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    val cameraReady = remember { mutableStateOf(false) }
    val captureBusy = remember { mutableStateOf(false) }
    val status = remember { mutableStateOf("Starting camera…") }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(captureExecutor) {
        onDispose { captureExecutor.shutdown() }
    }

    DisposableEffect(context, lifecycleOwner, previewView) {
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (!disposed) {
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val selector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 2560),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        )
                        .build()
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                        .setResolutionSelector(selector)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    boundProvider = provider
                    imageCapture.value = capture
                    cameraReady.value = true
                    status.value = "Align the document inside the frame."
                } catch (_: Exception) {
                    status.value = "Camera could not start. Check permission and camera availability."
                    cameraReady.value = false
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            imageCapture.value = null
            cameraReady.value = false
            runCatching { boundProvider?.unbindAll() }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Scan a document", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onClose, enabled = !captureBusy.value) {
                Text("Close")
            }
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 520.dp)
        )

        Text(status.value, style = MaterialTheme.typography.bodyMedium)

        Button(
            onClick = {
                val capture = imageCapture.value
                if (capture == null || captureBusy.value) return@Button
                captureBusy.value = true
                status.value = "Capturing in memory…"
                capture.takePicture(
                    captureExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                if (image.format != ImageFormat.JPEG) {
                                    throw IllegalStateException("Camera returned an unexpected image format")
                                }
                                val buffer = image.planes.firstOrNull()?.buffer
                                    ?: throw IllegalStateException("Camera returned an empty image")
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                if (bytes.size < 3 ||
                                    bytes[0] != 0xff.toByte() ||
                                    bytes[1] != 0xd8.toByte() ||
                                    bytes[2] != 0xff.toByte()
                                ) {
                                    throw IllegalStateException("Captured image is not a valid JPEG")
                                }
                                mainHandler.post {
                                    captureBusy.value = false
                                    status.value = "Photo captured. Encrypting locally…"
                                    onCaptured(bytes)
                                }
                            } catch (_: Exception) {
                                mainHandler.post {
                                    captureBusy.value = false
                                    status.value = "Capture failed. No document was saved."
                                }
                            } finally {
                                image.close()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            mainHandler.post {
                                captureBusy.value = false
                                status.value = "Camera capture failed. No document was saved."
                            }
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = cameraReady.value && !captureBusy.value
        ) {
            Text(if (captureBusy.value) "Capturing…" else "Capture page")
        }
    }
}
