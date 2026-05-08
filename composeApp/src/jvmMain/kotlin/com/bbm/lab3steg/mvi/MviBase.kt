package com.bbm.lab3steg.mvi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ViewState
interface ViewIntent
interface ViewEffect

interface Store<S : ViewState, I : ViewIntent, E : ViewEffect> {
    val state: StateFlow<S>
    val effect: Flow<E>
    fun sendIntent(intent: I)
}
