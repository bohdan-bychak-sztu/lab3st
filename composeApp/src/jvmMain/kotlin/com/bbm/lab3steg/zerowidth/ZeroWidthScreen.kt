package com.bbm.lab3steg.zerowidth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ZeroWidthScreen(store: ZeroWidthStore, snackbarHostState: SnackbarHostState) {
    val state by store.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val fileSaver = rememberFileSaverLauncher { file ->
        if (file != null) {
            // File saved
        }
    }

    val filePicker = rememberFilePickerLauncher(
        type = PickerType.File(extensions = listOf("txt"))
    ) { file ->
        if (file != null) {
            coroutineScope.launch {
                try {
                    val bytes = file.readBytes()
                    store.sendIntent(ZeroWidthIntent.UpdateOutputText(bytes.decodeToString()))
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Помилка зчитування файлу")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        store.effect.collectLatest { effect ->
            when (effect) {
                is ZeroWidthEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = state.containerText,
            onValueChange = { store.sendIntent(ZeroWidthIntent.UpdateContainerText(it)) },
            label = { Text("Контейнер (оригінальний текст)") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            maxLines = 10
        )

        OutlinedTextField(
            value = state.secretText,
            onValueChange = { store.sendIntent(ZeroWidthIntent.UpdateSecretText(it)) },
            label = { Text("Секретне повідомлення") },
            modifier = Modifier.fillMaxWidth().weight(1f),
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
            modifier = Modifier.fillMaxWidth().weight(1f),
            maxLines = 10
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { store.sendIntent(ZeroWidthIntent.Encode) }) {
                Text("Приховати")
            }
            Button(onClick = { store.sendIntent(ZeroWidthIntent.Decode) }) {
                Text("Витягти")
            }
        }

        OutlinedTextField(
            value = state.outputText,
            onValueChange = { store.sendIntent(ZeroWidthIntent.UpdateOutputText(it)) },
            label = { Text("Результат") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            maxLines = 10
        )

        Button(
            onClick = {
                fileSaver.launch(
                    baseName = "zero_width_result",
                    extension = "txt",
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
