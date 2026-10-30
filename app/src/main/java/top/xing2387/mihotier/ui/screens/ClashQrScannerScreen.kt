package top.xing2387.mihotier.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import top.xing2387.mihotier.ui.theme.AppTheme
import top.xing2387.mihotier.ui.utils.QrCodeUtils
import java.util.concurrent.Executors

@Composable
fun ClashQrScannerScreen(
    onNavigateBack: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "需要相机权限以扫描二维码", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isScanned by remember { mutableStateOf(false) }

    fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(60L)
            }
        } catch (_: Exception) {}
    }

    fun handleScanSuccess(text: String) {
        if (isScanned) return
        isScanned = true
        triggerVibration()
        onQrScanned(text)
    }

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val decoded = QrCodeUtils.decodeQrCodeFromBitmap(bitmap)
                    if (!decoded.isNullOrBlank()) {
                        handleScanSuccess(decoded)
                    } else {
                        Toast.makeText(context, "未能从图片中识别到二维码", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "无法读取所选图片", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "解析图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            // 1. CameraX PreviewView
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val cameraExecutor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder()
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (!isScanned) {
                                            processImageProxy(imageProxy) { qrText ->
                                                if (!isScanned) {
                                                    previewView.post {
                                                        handleScanSuccess(qrText)
                                                    }
                                                }
                                            }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Viewfinder Mask & Scan Animation Overlay
            ScannerOverlay(
                boxSize = 260.dp,
                accentColor = AppTheme.colors.brandPrimary
            )
        } else {
            // Permission Denied View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "需要相机权限",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请授予相机权限以直接扫描配置文件或订阅二维码",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brandPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("开启相机权限", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(42.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            }

            Text(
                text = "扫描二维码",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Torch Switch
                if (hasCameraPermission) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(onClick = {
                            val nextTorch = !isTorchOn
                            camera?.cameraControl?.enableTorch(nextTorch)
                            isTorchOn = nextTorch
                        }) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "手电筒",
                                tint = if (isTorchOn) AppTheme.colors.brandPrimary else Color.White
                            )
                        }
                    }
                }

                // Gallery Picker
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "从相册选择",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay(
    boxSize: Dp = 260.dp,
    accentColor: Color
) {
    val density = LocalDensity.current
    val boxSizePx = with(density) { boxSize.toPx() }

    val transition = rememberInfiniteTransition(label = "Laser")
    val laserProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserProgress"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        val left = (width - boxSizePx) / 2f
        val top = (height - boxSizePx) / 2.3f

        // 1. Semi-transparent dark background with clear rounded cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f) // enables blend mode
        ) {
            // Draw dimmed full background
            drawRect(color = Color.Black.copy(alpha = 0.65f))

            // Cut out scanner window
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSizePx, boxSizePx),
                cornerRadius = CornerRadius(20.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Draw rounded frame border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(left, top),
                size = Size(boxSizePx, boxSizePx),
                cornerRadius = CornerRadius(20.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Draw 4 stylish Corner Brackets (L-shapes)
            val cornerLen = 24.dp.toPx()
            val strokeW = 4.dp.toPx()
            val rad = 20.dp.toPx()

            // Top-Left
            drawLine(accentColor, Offset(left + rad, top), Offset(left + rad + cornerLen, top), strokeW)
            drawLine(accentColor, Offset(left, top + rad), Offset(left, top + rad + cornerLen), strokeW)
            drawArc(accentColor, 180f, 90f, false, Offset(left, top), Size(rad * 2, rad * 2), style = Stroke(strokeW))

            // Top-Right
            drawLine(accentColor, Offset(left + boxSizePx - rad - cornerLen, top), Offset(left + boxSizePx - rad, top), strokeW)
            drawLine(accentColor, Offset(left + boxSizePx, top + rad), Offset(left + boxSizePx, top + rad + cornerLen), strokeW)
            drawArc(accentColor, 270f, 90f, false, Offset(left + boxSizePx - rad * 2, top), Size(rad * 2, rad * 2), style = Stroke(strokeW))

            // Bottom-Left
            drawLine(accentColor, Offset(left + rad, top + boxSizePx), Offset(left + rad + cornerLen, top + boxSizePx), strokeW)
            drawLine(accentColor, Offset(left, top + boxSizePx - rad - cornerLen), Offset(left, top + boxSizePx - rad), strokeW)
            drawArc(accentColor, 90f, 90f, false, Offset(left, top + boxSizePx - rad * 2), Size(rad * 2, rad * 2), style = Stroke(strokeW))

            // Bottom-Right
            drawLine(accentColor, Offset(left + boxSizePx - rad - cornerLen, top + boxSizePx), Offset(left + boxSizePx - rad, top + boxSizePx), strokeW)
            drawLine(accentColor, Offset(left + boxSizePx, top + boxSizePx - rad - cornerLen), Offset(left + boxSizePx, top + boxSizePx - rad), strokeW)
            drawArc(accentColor, 0f, 90f, false, Offset(left + boxSizePx - rad * 2, top + boxSizePx - rad * 2), Size(rad * 2, rad * 2), style = Stroke(strokeW))
        }

        // 2. Animated Laser Line inside cutout
        val laserOffsetY = with(density) { (top + laserProgress * (boxSizePx - 20.dp.toPx()) + 10.dp.toPx()).toDp() }
        val laserOffsetX = with(density) { (left + 16.dp.toPx()).toDp() }
        val laserWidth = boxSize - 32.dp

        Box(
            modifier = Modifier
                .offset(x = laserOffsetX, y = laserOffsetY)
                .width(laserWidth)
                .height(3.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0f),
                            accentColor,
                            accentColor,
                            accentColor.copy(alpha = 0f)
                        )
                    ),
                    shape = RoundedCornerShape(2.dp)
                )
        )

        // 3. User Guidance Text below viewfinder
        val textOffsetY = with(density) { (top + boxSizePx + 24.dp.toPx()).toDp() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = textOffsetY)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "将二维码放入框内即可自动识别",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * CameraX ImageProxy decoder utilizing ZXing PlanarYUVLuminanceSource.
 */
private fun processImageProxy(imageProxy: ImageProxy, onFound: (String) -> Unit) {
    try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val width = imageProxy.width
        val height = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees

        // Handle rotation for Y luminance bytes
        val (rotatedBytes, rotWidth, rotHeight) = when (rotation) {
            90 -> {
                val rotated = ByteArray(bytes.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[x * height + (height - y - 1)] = bytes[y * width + x]
                    }
                }
                Triple(rotated, height, width)
            }
            180 -> {
                val rotated = ByteArray(bytes.size)
                for (i in 0 until bytes.size) {
                    rotated[bytes.size - 1 - i] = bytes[i]
                }
                Triple(rotated, width, height)
            }
            270 -> {
                val rotated = ByteArray(bytes.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[(width - x - 1) * height + y] = bytes[y * width + x]
                    }
                }
                Triple(rotated, height, width)
            }
            else -> Triple(bytes, width, height)
        }

        val source = PlanarYUVLuminanceSource(
            rotatedBytes,
            rotWidth,
            rotHeight,
            0,
            0,
            rotWidth,
            rotHeight,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"))
        }

        val result = reader.decodeWithState(binaryBitmap)
        if (!result.text.isNullOrBlank()) {
            onFound(result.text)
        }
    } catch (_: Exception) {
    } finally {
        imageProxy.close()
    }
}
