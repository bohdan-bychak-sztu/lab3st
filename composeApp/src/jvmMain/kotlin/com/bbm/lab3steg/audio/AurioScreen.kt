package com.bbm.lab3steg.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun AudioScreen(store: AudioStore, snackbarHostState: SnackbarHostState) {
    val state by store.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val fileSaver = rememberFileSaverLauncher { file ->
        if (file != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Файл збережено") }
        }
    }

    val filePicker = rememberFilePickerLauncher(type = PickerType.File(extensions = listOf("wav"))) { file ->
        if (file != null) {
            coroutineScope.launch {
                try {
                    val bytes = file.readBytes()
                    store.sendIntent(AudioIntent.LoadAudio(file.name, bytes))
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Помилка зчитування файлу")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        store.effect.collectLatest { effect ->
            when (effect) {
                is AudioEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { filePicker.launch() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.audioFileName.isEmpty()) "Завантажити WAV файл-контейнер" else "Оригінал: ${state.audioFileName}")
        }

        if (state.originalBytes != null) {
            Text("Графік хвилі (Оригінал):", fontWeight = FontWeight.Bold)
            AudioWaveform(state.originalBytes)
        }

        if (state.encodedBytes != null) {
            Text("Графік хвилі (Із прихованим текстом):", fontWeight = FontWeight.Bold)
            AudioWaveform(state.encodedBytes, color = Color.Red)
        }

        OutlinedTextField(
            value = state.secretText,
            onValueChange = { store.sendIntent(AudioIntent.UpdateSecretText(it)) },
            label = { Text("Секретне повідомлення") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 10
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.useGamma,
                onCheckedChange = { store.sendIntent(AudioIntent.UpdateUseGamma(it)) }
            )
            Text("Використовувати гамування перед приховуванням")
        }

        if (state.useGamma) {
            OutlinedTextField(
                value = state.gammaKey,
                onValueChange = { store.sendIntent(AudioIntent.UpdateGammaKey(it)) },
                label = { Text("Ключ гамування") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = state.stegoKey,
            onValueChange = { store.sendIntent(AudioIntent.UpdateStegoKey(it)) },
            label = { Text("Ключ розсіяння LSB") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { store.sendIntent(AudioIntent.Encode) }) {
                Text("Приховати (LSB)")
            }
            Button(onClick = { store.sendIntent(AudioIntent.Decode) }) {
                Text("Витягти (LSB)")
            }
        }

        if (state.encodedBytes != null) {
            Button(
                onClick = {
                    fileSaver.launch(
                        baseName = "stego_audio",
                        extension = "wav",
                        bytes = state.encodedBytes
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Зберегти змінений WAV файл")
            }
        }
    }
}

@Composable
fun AudioWaveform(bytes: ByteArray?, color: Color = Color.Blue) {
    if (bytes == null || bytes.size < 44) return

    // Витягуємо спрощену амплітуду для Canvas, щоб не навантажувати UI
    val amplitudes = remember(bytes) {
        val points = 400
        val dataSize = bytes.size - 44
        val step = (dataSize / points).coerceAtLeast(1)
        val result = mutableListOf<Float>()

        for (i in 44 until bytes.size step step) {
            val amp = kotlin.math.abs(bytes[i].toInt()) / 128f
            result.add(amp.coerceIn(0f, 1f))
        }
        result
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val width = size.width
        val height = size.height
        val barWidth = width / amplitudes.size

        amplitudes.forEachIndexed { index, amp ->
            val thisX = index * barWidth
            val thisY = height / 2f
            val barHeight = (amp * height).coerceAtLeast(2f)

            drawLine(
                color = color,
                start = Offset(thisX, thisY - barHeight / 2),
                end = Offset(thisX, thisY + barHeight / 2),
                strokeWidth = barWidth * 0.8f
            )
        }
    }
}
