package com.bbm.lab3steg

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

import com.bbm.lab3steg.zerowidth.ZeroWidthScreen
import com.bbm.lab3steg.zerowidth.ZeroWidthStore
import com.bbm.lab3steg.casechange.CaseChangeScreen
import com.bbm.lab3steg.casechange.CaseChangeStore
import com.bbm.lab3steg.spacemanip.SpaceManipScreen
import com.bbm.lab3steg.spacemanip.SpaceManipStore
import com.bbm.lab3steg.colormanip.ColorManipScreen
import com.bbm.lab3steg.colormanip.ColorManipStore
import com.bbm.lab3steg.audio.AudioScreen
import com.bbm.lab3steg.audio.AudioStore

@Composable
@Preview
fun App() {
    MaterialTheme {
        var selectedTabIndex by remember { mutableStateOf(0) }
        val tabs = listOf("Zero-Width", "Зміна регістру", "Маніпуляція пробілами", "Зміна кольору HTML", "Аудіо LSB")
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        // Stores
        val zeroWidthStore = remember { ZeroWidthStore(coroutineScope) }
        val caseChangeStore = remember { CaseChangeStore(coroutineScope) }
        val spaceManipStore = remember { SpaceManipStore(coroutineScope) }
        val colorManipStore = remember { ColorManipStore(coroutineScope) }
        val audioStore = remember { AudioStore(coroutineScope) }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                when (selectedTabIndex) {
                    0 -> ZeroWidthScreen(zeroWidthStore, snackbarHostState)
                    1 -> CaseChangeScreen(caseChangeStore, snackbarHostState)
                    2 -> SpaceManipScreen(spaceManipStore, snackbarHostState)
                    3 -> ColorManipScreen(colorManipStore, snackbarHostState)
                    4 -> AudioScreen(audioStore, snackbarHostState)
                }
            }
        }
    }
}