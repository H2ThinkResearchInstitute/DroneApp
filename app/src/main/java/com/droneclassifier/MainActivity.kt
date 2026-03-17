*** Begin Patch
*** Delete File: app/src/main/java/com/droneclassifier/MainActivity.kt
*** End Patch

*** Begin Patch
*** Add File: app/src/main/java/com/droneclassifier/MainActivity.kt
package com.droneclassifier

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.droneclassifier.ui.theme.DroneClassifierTheme
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.delay
import java.io.File
import java.nio.FloatBuffer

/**
 * MainActivity sets up permissions and hosts the Compose UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroneClassifierTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppBar()
                    // Request microphone and storage permissions at runtime
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(android.Manifest.permission.RECORD_AUDIO),
                            1337
                        )
                    }
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                            111
                        )
                    }
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            222
                        )
                    }
                }
            }
        }
    }
}

/**
 * A button that toggles audio classification of the selected ONNX model.
 */
@Composable
fun RunClassifierButton(
    fileName: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // UI state controlling whether classification is running and showing the result
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisState by remember { mutableStateOf("Start") }
    val icon = if (isAnalyzing) Icons.Filled.Call else Icons.Filled.PlayArrow
    val context = LocalContext.current

    // Initialize the ONNX runtime environment and session when the model file name changes.
    val env = remember { OrtEnvironment.getEnvironment() }
    val session = remember(fileName) {
        // Copy the selected ONNX model from assets to internal storage (only once)
        val tempFile = File(context.filesDir, fileName)
        if (!tempFile.exists()) {
            context.assets.open(fileName).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        env.createSession(tempFile.absolutePath, OrtSession.SessionOptions())
    }

    // Prepare audio recorder and buffers
    val sampleRate = 16_000
    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    val recorder = remember {
        AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }
    val audioBuffer = remember { ShortArray(sampleRate / 2) }
    val floatBuffer = remember { FloatArray(sampleRate / 2) }

    // Layout for the toggle button
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Button(
            onClick = { isAnalyzing = !isAnalyzing },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0x0A, 0x4A, 0x73),
                contentColor = Color(0x0F, 0x10, 0x0A),
            ),
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.CenterHorizontally)
                .size(200.dp),
            shape = CircleShape,
            enabled = enabled
        ) {
            Icon(icon, contentDescription = "Toggle classification")
            Text(text = analysisState)
        }
    }

    /**
     * Clean up the recorder and session when this composable leaves the composition
     * or when a new model is selected.
     */
    DisposableEffect(fileName) {
        onDispose {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
            recorder.release()
            session.close()
        }
    }

    /**
     * Start or stop audio classification whenever the `isAnalyzing` flag changes.
     * The coroutine launched by `LaunchedEffect` automatically cancels itself when
     * `isAnalyzing` becomes false or when the model file name changes.
     */
    LaunchedEffect(isAnalyzing, fileName) {
        if (isAnalyzing) {
            recorder.startRecording()
            val inputName = session.inputNames.iterator().next()
            try {
                while (true) {
                    // Read audio samples and normalise to float values
                    val read = recorder.read(audioBuffer, 0, audioBuffer.size)
                    for (i in 0 until read) {
                        floatBuffer[i] = audioBuffer[i] / 32768.0f
                    }
                    try {
                        val tensor = OnnxTensor.createTensor(
                            env,
                            FloatBuffer.wrap(floatBuffer),
                            longArrayOf(1, read.toLong())
                        )
                        val results = session.run(mapOf(inputName to tensor))
                        val rawOutput = results[0].value
                        val probabilities: FloatArray = when (rawOutput) {
                            is FloatArray -> rawOutput
                            is Array<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                (rawOutput as Array<FloatArray>)[0]
                            }
                            else -> floatArrayOf()
                        }
                        if (probabilities.isNotEmpty()) {
                            var maxIndex = 0
                            var maxValue = probabilities[0]
                            for (i in 1 until probabilities.size) {
                                if (probabilities[i] > maxValue) {
                                    maxIndex = i
                                    maxValue = probabilities[i]
                                }
                            }
                            val label = if (maxIndex == 1) "drone" else "other"
                            analysisState = "$label -> ${'$'}maxValue"
                            // Beep when a drone is detected
                            if (label == "drone") {
                                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                                // play a short tone; only one tone plays at a time【92818980510963†L95-L97】【92818980510963†L159-L161】
                                toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
                            }
                        }
                        tensor.close()
                        results.close()
                    } catch (e: Exception) {
                        analysisState = "Error: ${'$'}{e.message}"
                    }
                    // wait before processing the next chunk
                    delay(500)
                }
            } finally {
                // Ensure recorder stops when coroutine is cancelled
                try {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                } catch (_: Exception) {
                }
            }
        } else {
            // Reset state when not analysing
            analysisState = "Start"
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Top-level UI showing the app bar, file selection and classification controls.
 */
@SuppressLint("IntentReset")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar() {
    val smallLogo = painterResource(R.drawable.small_logo)
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf("Select a file") }
    var fileList by remember { mutableStateOf(listOf<String>()) }
    var showFileList by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x0A, 0x4A, 0x73),
                    titleContentColor = Color(0x0F, 0x10, 0x0A),
                ),
                title = {
                    Text(
                        "H2THINK",
                        maxLines = 1,
                        letterSpacing = 14.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Exit App"
                        )
                    }
                },
                actions = {
                    Icon(
                        painter = smallLogo,
                        contentDescription = null,
                        tint = Color(0x0F, 0x10, 0x0A),
                        modifier = Modifier.size(40.dp)
                    )
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    // Button to load available ONNX models from assets
                    ExtendedFloatingActionButton(
                        onClick = {
                            fileList = getOnnxFiles(context)
                            if (fileList.isNotEmpty()) {
                                showFileList = true
                            }
                        },
                        containerColor = Color(0x0A, 0x4A, 0x73),
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            tint = Color(0x0F, 0x10, 0x0A)
                        )
                        Text(
                            text = selectedFile,
                            fontSize = 14.sp,
                            color = Color(0x0F, 0x10, 0x0A),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                },
                floatingActionButton = {
                    // Placeholder for additional actions; currently no-op
                    FloatingActionButton(
                        onClick = {
                            // The plus button is reserved for future functionality.
                        },
                        containerColor = Color(0x0A, 0x4A, 0x73),
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color(0x0F, 0x10, 0x0A)
                        )
                    }
                },
                containerColor = Color(0x04, 0x28, 0x3F)
            )
        },
        containerColor = Color(0x04, 0x28, 0x3F)
    ) { innerPadding ->
        RunClassifierButton(
            fileName = selectedFile,
            enabled = selectedFile != "Select a file",
            modifier = Modifier.padding(innerPadding)
        )
        if (showFileList) {
            FileListDialog(
                fileList = fileList,
                onDismiss = { showFileList = false },
                onFileSelected = { fileName ->
                    selectedFile = fileName
                    showFileList = false
                }
            )
        }
    }
}

/**
 * Utility to list ONNX model files stored in the assets folder.
 */
private fun getOnnxFiles(context: Context): List<String> {
    return context.assets.list("")?.toList()?.filter { it.endsWith(".onnx") } ?: emptyList()
}

/**
 * Dialog that shows a list of available ONNX models for the user to choose from.
 */
@Composable
fun FileListDialog(
    fileList: List<String>,
    onDismiss: () -> Unit,
    onFileSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Select a Model") },
        text = {
            Column {
                fileList.forEach { fileName ->
                    TextButton(onClick = { onFileSelected(fileName) }) {
                        Text(text = fileName)
                    }
                }
            }
        },
        confirmButton = { }
    )
}

@Preview(showBackground = true)
@Composable
fun DroneClassifierPreview() {
    DroneClassifierTheme {
        AppBar()
    }
}
