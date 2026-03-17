package com.droneclassifier

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
//import org.tensorflow.lite.task.audio.classifier.AudioClassifier
// Removed TensorFlow Lite import and replaced with ONNX runtime and audio APIs.
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtException
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.ToneGenerator
import java.nio.FloatBuffer
import java.io.File
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroneClassifierTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppBar()
                    if (ContextCompat.checkSelfPermission(this@MainActivity,
                                                          android.Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED){
                        ActivityCompat.requestPermissions(this@MainActivity,
                                                          arrayOf(android.Manifest.permission.RECORD_AUDIO),
                                                          1337)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED){
                        ActivityCompat.requestPermissions(this@MainActivity,
                            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                            111)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED){
                        ActivityCompat.requestPermissions(this@MainActivity,
                            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            222)
                    }
                }
            }
        }
    }


@Composable
fun RunClassifierButton(fileName: String, enabled: Boolean, modifier: Modifier = Modifier) {
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisState by remember { mutableStateOf("Start") }
    val icon = if (isAnalyzing) Icons.Filled.Call else Icons.Filled.PlayArrow
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Button(
            onClick = { isAnalyzing = !isAnalyzing },
            //containerColor = Color(0x0A, 0x4A, 0x73),
            //contentColor = Color(0x0F, 0x10, 0x0A),
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
            // Replace with the icon or text you want, or make it empty for a clean button
            Icon(icon, contentDescription = "Localized description")
            Text(text = analysisState)
        }
    }

    if (isAnalyzing) {
        // Initialise ONNX runtime environment and session once using remember
        val env = remember { OrtEnvironment.getEnvironment() }
        val session = remember(fileName) {
            // Copy the ONNX model from assets to a temporary file before opening it
            val tempFile = File(context.filesDir, fileName)
            if (!tempFile.exists()) {
                context.assets.open(fileName).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            env.createSession(tempFile.absolutePath, OrtSession.SessionOptions())
        }

        // Configure audio recording: 16 kHz mono 16‑bit PCM
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
        // Buffers to hold half a second of audio (8 k samples)
        val audioBuffer = ShortArray(sampleRate / 2)
        val floatBuffer = FloatArray(sampleRate / 2)
        recorder.startRecording()
        LaunchedEffect(isAnalyzing) {
            val timer = Timer()
            timer.scheduleAtFixedRate(1, 500) {
                if (!isAnalyzing) {
                    analysisState = "Start"
                    // release resources when stopping
                    recorder.stop()
                    recorder.release()
                    session.close()
                    timer.cancel()
                } else {
                    // Read audio samples into the buffer
                    val read = recorder.read(audioBuffer, 0, audioBuffer.size)
                    for (i in 0 until read) {
                        // normalise 16‑bit PCM to -1..1 floats
                        floatBuffer[i] = audioBuffer[i] / 32768.0f
                    }
                    try {
                        // Create an input tensor with shape [1, numberOfSamples]
                        val tensor = OnnxTensor.createTensor(
                            env,
                            FloatBuffer.wrap(floatBuffer),
                            longArrayOf(1, read.toLong())
                        )
                        val inputName = session.inputNames.iterator().next()
                        val results = session.run(mapOf(inputName to tensor))

                        // Assume the model outputs a 1D array of scores. Extract the first output tensor.
                        val probabilities = (results[0].value as Array<FloatArray>)[0]

                        // Find the index of the maximum score
                        var maxIndex = 0
                        var maxValue = probabilities[0]
                        for (i in 1 until probabilities.size) {
                            if (probabilities[i] > maxValue) {
                                maxIndex = i
                                maxValue = probabilities[i]
                            }
                        }
                        // Map index to label: index 1 -> drone, anything else -> other
                        val label = if (maxIndex == 1) "drone" else "other"
                        val outputStr = "$label -> $maxValue"
                        if (outputStr.isNotEmpty()) {
                            analysisState = outputStr
                        }
                        // Play a short beep when a drone is detected
                        if (label == "drone") {
                            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
                        }
                        // Close tensor and results to free native resources
                        tensor.close()
                        results.close()
                    } catch (e: OrtException) {
                        // Show error in UI
                        analysisState = "Error: ${'$'}{e.message}"
                    }
                }
            }
        }
    } else {
        analysisState = "Start" // Reset the text when not analyzing
    }
}

@SuppressLint("IntentReset")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar() {
    val smallLogo = painterResource(R.drawable.small_logo)
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf("Select a file")}
    var fileList by remember { mutableStateOf(listOf<String>())}
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
                    IconButton(onClick = { exitProcess(0) }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Exit App"
                        )
                    }
                },
                actions = {
                    Icon(painter = smallLogo,
                         contentDescription = null,
                         tint = Color(0x0F, 0x10, 0x0A),
                         modifier = Modifier.size(40.dp))
                }
            )
        },

        bottomBar = {
            BottomAppBar(
                actions = {
                    ExtendedFloatingActionButton(
                        onClick = {
                            // List ONNX models instead of TFLite models
                            fileList = getOnnxFiles(context)
                            if (fileList.isNotEmpty()) {
                                showFileList = true
                            }},
                        containerColor = Color(0x0A, 0x4A, 0x73),
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(Icons.Filled.Build,
                             contentDescription = null,
                             tint = Color(0x0F, 0x10, 0x0A))
                        Text(text = selectedFile,
                             fontSize = 14.sp,
                             color = Color(0x0F, 0x10, 0x0A),
                             modifier = Modifier.padding(8.dp))
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                                navigateFiles()

                            /* do something */ },
                        containerColor = Color(0x0A, 0x4A, 0x73),
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    ) {
                        Icon(Icons.Filled.Add,
                             contentDescription = null,
                             tint = Color(0x0F, 0x10, 0x0A))
                    }
                },
                containerColor = Color(0x04, 0x28, 0x3F) // 04283F
            )
        },
        containerColor = Color(0x04, 0x28, 0x3F) // 0A4A73
    ) {
        innerPadding -> RunClassifierButton(fileName = selectedFile,
                                            enabled = selectedFile != "Select a file",
                                            modifier = Modifier.padding(innerPadding))
        if (showFileList)
        {
            FileListDialog(fileList = fileList,
                onDismiss = { showFileList = false },
                onFileSelected = {fileName ->
                    selectedFile = fileName
                    showFileList = false
                })
        }
    }
}

private fun navigateFiles(){

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        startActivityForResult(intent, 111)


    //to-do
    // import selected file to the fileList so it can be selected
}


private fun getOnnxFiles(context: Context): List<String> {
    // Filter the assets directory for files ending in `.onnx`
    return context.assets.list("")?.toList()?.filter { it.endsWith(".onnx") } ?: emptyList()
}

@Composable
fun FileListDialog(fileList: List<String>,
                   onDismiss: () -> Unit,
                   onFileSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Select a Model") },
        text = {
            Column {
                fileList.forEach { fileName ->
                    TextButton(onClick = { onFileSelected(fileName) }) { Text(text = fileName) }
                }
            }
        },
        confirmButton = { /* Not needed */}
    )
}

@Preview(showBackground = true)
@Composable
fun DroneClassifierPreview() {
    DroneClassifierTheme {
        AppBar()
    }
}
}



