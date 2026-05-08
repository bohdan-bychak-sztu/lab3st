package com.bbm.lab3steg.zerowidth

import com.bbm.lab3steg.mvi.Store
import com.bbm.lab3steg.mvi.ViewEffect
import com.bbm.lab3steg.mvi.ViewIntent
import com.bbm.lab3steg.mvi.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bbm.lab3steg.utils.performGammaArithmetic

data class ZeroWidthState(
    val containerText: String = "",
    val secretText: String = "",
    val outputText: String = "",
    val useGamma: Boolean = false,
    val gammaKey: String = "",
    val gammaLanguage: String = "Ukrainian",
    val gammaFormula: String = "S = Г + О"
) : ViewState

sealed class ZeroWidthIntent : ViewIntent {
    data class UpdateContainerText(val text: String) : ZeroWidthIntent()
    data class UpdateSecretText(val text: String) : ZeroWidthIntent()
    data class UpdateOutputText(val text: String) : ZeroWidthIntent()
    data class UpdateUseGamma(val use: Boolean) : ZeroWidthIntent()
    data class UpdateGammaKey(val text: String) : ZeroWidthIntent()
    object Encode : ZeroWidthIntent()
    object Decode : ZeroWidthIntent()
}

sealed class ZeroWidthEffect : ViewEffect {
    data class ShowMessage(val message: String) : ZeroWidthEffect()
}

class ZeroWidthStore(private val coroutineScope: CoroutineScope) : Store<ZeroWidthState, ZeroWidthIntent, ZeroWidthEffect> {

    private val _state = MutableStateFlow(ZeroWidthState())
    override val state: StateFlow<ZeroWidthState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ZeroWidthEffect>()
    override val effect: Flow<ZeroWidthEffect> = _effect.asSharedFlow()

    override fun sendIntent(intent: ZeroWidthIntent) {
        when (intent) {
            is ZeroWidthIntent.UpdateContainerText -> {
                _state.update { it.copy(containerText = intent.text) }
            }
            is ZeroWidthIntent.UpdateSecretText -> {
                _state.update { it.copy(secretText = intent.text) }
            }
            is ZeroWidthIntent.UpdateOutputText -> {
                _state.update { it.copy(outputText = intent.text) }
            }
            is ZeroWidthIntent.UpdateUseGamma -> _state.update { it.copy(useGamma = intent.use) }
            is ZeroWidthIntent.UpdateGammaKey -> _state.update { it.copy(gammaKey = intent.text) }
            ZeroWidthIntent.Encode -> {
                encodeText()
            }
            ZeroWidthIntent.Decode -> {
                decodeText()
            }
        }
    }

    private fun encodeText() {
        val st = _state.value
        val container = st.containerText
        val originalSecret = st.secretText

        if (container.isEmpty()) {
            emitEffect(ZeroWidthEffect.ShowMessage("Контейнер порожній"))
            return
        }
        if (originalSecret.isEmpty()) {
            emitEffect(ZeroWidthEffect.ShowMessage("Секретне повідомлення порожнє"))
            return
        }

        val secret = if (st.useGamma) {
            if (st.gammaKey.isEmpty()) {
                emitEffect(ZeroWidthEffect.ShowMessage("Ключ гамування порожній"))
                return
            }
            performGammaArithmetic(originalSecret, st.gammaKey, st.gammaLanguage, st.gammaFormula, true)
        } else originalSecret

        val zwsp = '\u200B'
        val zwnj = '\u200C'

        val binarySecret = secret.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            byte.toUByte().toString(2).padStart(8, '0')
        }

        val hiddenString = binarySecret.map { if (it == '1') zwsp else zwnj }.joinToString("")

        val insertIndex = container.length / 2
        val encodedResult = container.substring(0, insertIndex) + hiddenString + container.substring(insertIndex)

        _state.update { it.copy(outputText = encodedResult) }
        emitEffect(ZeroWidthEffect.ShowMessage("Успішно приховано"))
    }

    private fun decodeText() {
        val st = _state.value
        val encodedText = st.outputText.ifEmpty { st.containerText }

        val zwsp = '\u200B'
        val zwnj = '\u200C'

        val binaryBuilder = StringBuilder()
        for (char in encodedText) {
            if (char == zwsp) binaryBuilder.append('1')
            else if (char == zwnj) binaryBuilder.append('0')
        }

        if (binaryBuilder.length % 8 != 0) {
            emitEffect(ZeroWidthEffect.ShowMessage("Не знайдено прихованого повідомлення або воно пошкоджене"))
            return
        }

        if (binaryBuilder.isEmpty()) {
            emitEffect(ZeroWidthEffect.ShowMessage("Прихованого повідомлення не знайдено"))
            return
        }

        try {
            val bytes = binaryBuilder.chunked(8).map { it.toInt(2).toByte() }.toByteArray()
            val decodedSecretBytes = String(bytes, Charsets.UTF_8)
            val decodedSecret = if (st.useGamma) {
                if (st.gammaKey.isEmpty()) {
                    emitEffect(ZeroWidthEffect.ShowMessage("Для декодування потрібен ключ"))
                    return
                }
                performGammaArithmetic(decodedSecretBytes, st.gammaKey, st.gammaLanguage, st.gammaFormula, false)
            } else decodedSecretBytes
            _state.update { it.copy(secretText = decodedSecret) }
            emitEffect(ZeroWidthEffect.ShowMessage("Успішно декодовано"))
        } catch (e: Exception) {
            emitEffect(ZeroWidthEffect.ShowMessage("Помилка декодування"))
        }
    }

    private fun emitEffect(effect: ZeroWidthEffect) {
        coroutineScope.launch { _effect.emit(effect) }
    }
}
