package com.bbm.lab3steg.colormanip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ColorManipScreen(store: ColorManipStore, snackbarHostState: SnackbarHostState) {
    val state by store.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val fileSaver = rememberFileSaverLauncher { file ->
        if (file != null) {
            // File saved
        }
    }

    val filePicker = rememberFilePickerLauncher(
        type = PickerType.File(extensions = listOf("html", "htm", "txt"))
    ) { file ->
        if (file != null) {
            coroutineScope.launch {
                try {
                    val bytes = file.readBytes()
                    store.sendIntent(ColorManipIntent.UpdateOutputText(bytes.decodeToString()))
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar("Помилка зчитування файлу")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        store.effect.collectLatest { effect ->
            when (effect) {
                is ColorManipEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
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
        OutlinedTextField(
            value = state.containerText,
            onValueChange = { store.sendIntent(ColorManipIntent.UpdateContainerText(it)) },
            label = { Text("Контейнер (оригінальний текст)") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 10
        )

        OutlinedTextField(
            value = state.secretText,
            onValueChange = { store.sendIntent(ColorManipIntent.UpdateSecretText(it)) },
            label = { Text("Секретне повідомлення") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 10
        )

        val binarySecret = remember(state.secretText) {
            state.secretText.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
                byte.toUByte().toString(2).padStart(8, '0')
            }
        }

        OutlinedTextField(
            value = binarySecret,
            onValueChange = {},
            readOnly = true,
            label = { Text("Секретне повідомлення (бінарний вигляд)") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 10
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.useGamma,
                onCheckedChange = { store.sendIntent(ColorManipIntent.UpdateUseGamma(it)) }
            )
            Text("Використовувати гамування перед приховуванням")
        }

        if (state.useGamma) {
            OutlinedTextField(
                value = state.gammaKey,
                onValueChange = { store.sendIntent(ColorManipIntent.UpdateGammaKey(it)) },
                label = { Text("Ключ гамування") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { store.sendIntent(ColorManipIntent.Encode) }) {
                Text("Приховати (в HTML)")
            }
            Button(onClick = { store.sendIntent(ColorManipIntent.Decode) }) {
                Text("Витягти (з HTML)")
            }
        }

        OutlinedTextField(
            value = state.outputText,
            onValueChange = { store.sendIntent(ColorManipIntent.UpdateOutputText(it)) },
            label = { Text("Результат (HTML-код)") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 10
        )

        Button(
            onClick = {
                fileSaver.launch(
                    baseName = "color_manip_result",
                    extension = "html",
                    bytes = state.outputText.toByteArray()
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Зберегти результат у файл")
        }

        Button(
            onClick = { filePicker.launch() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Відкрити файл для декодування")
        }
    }
}
