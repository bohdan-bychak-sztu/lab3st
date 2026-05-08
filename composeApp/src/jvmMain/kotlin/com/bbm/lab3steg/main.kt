package com.bbm.lab3steg

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "lab3steg",
    ) {
        App()
    }
}