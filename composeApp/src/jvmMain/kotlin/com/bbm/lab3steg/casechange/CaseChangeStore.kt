package com.bbm.lab3steg.casechange

import com.bbm.lab3steg.mvi.Store
import com.bbm.lab3steg.mvi.ViewEffect
import com.bbm.lab3steg.mvi.ViewIntent
import com.bbm.lab3steg.mvi.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bbm.lab3steg.utils.performGammaArithmetic

data class CaseChangeState(
    val containerText: String = "",
    val secretText: String = "",
    val outputText: String = "",
    val useGamma: Boolean = false,
    val gammaKey: String = "",
    val gammaLanguage: String = "Ukrainian",
    val gammaFormula: String = "S = Г + О"
) : ViewState

sealed class CaseChangeIntent : ViewIntent {
    data class UpdateContainerText(val text: String) : CaseChangeIntent()
    data class UpdateSecretText(val text: String) : CaseChangeIntent()
    data class UpdateOutputText(val text: String) : CaseChangeIntent()
    data class UpdateUseGamma(val use: Boolean) : CaseChangeIntent()
    data class UpdateGammaKey(val text: String) : CaseChangeIntent()
    object Encode : CaseChangeIntent()
    object Decode : CaseChangeIntent()
}

sealed class CaseChangeEffect : ViewEffect {
    data class ShowMessage(val message: String) : CaseChangeEffect()
}

class CaseChangeStore(private val coroutineScope: CoroutineScope) : Store<CaseChangeState, CaseChangeIntent, CaseChangeEffect> {
    private val _state = MutableStateFlow(CaseChangeState())
    override val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<CaseChangeEffect>()
    override val effect = _effect.asSharedFlow()

    override fun sendIntent(intent: CaseChangeIntent) {
        when (intent) {
            is CaseChangeIntent.UpdateContainerText -> _state.update { it.copy(containerText = intent.text) }
            is CaseChangeIntent.UpdateSecretText -> _state.update { it.copy(secretText = intent.text) }
            is CaseChangeIntent.UpdateOutputText -> _state.update { it.copy(outputText = intent.text) }
            is CaseChangeIntent.UpdateUseGamma -> _state.update { it.copy(useGamma = intent.use) }
            is CaseChangeIntent.UpdateGammaKey -> _state.update { it.copy(gammaKey = intent.text) }
            CaseChangeIntent.Encode -> encodeText()
            CaseChangeIntent.Decode -> decodeText()
        }
    }

    private fun encodeText() {
        val st = _state.value
        val container = st.containerText
        val originalSecret = st.secretText

        if (container.isEmpty() || originalSecret.isEmpty()) {
            emitEffect(CaseChangeEffect.ShowMessage("Контейнер або повідомлення порожні"))
            return
        }

        val secret = if (st.useGamma) {
            if (st.gammaKey.isEmpty()) {
                emitEffect(CaseChangeEffect.ShowMessage("Ключ гамування порожній"))
                return
            }
            performGammaArithmetic(originalSecret, st.gammaKey, st.gammaLanguage, st.gammaFormula, true)
        } else {
            originalSecret
        }

        val binarySecret = secret.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            byte.toUByte().toString(2).padStart(8, '0')
        }
        val binaryFull = binarySecret + "00000000"

        val lettersCount = container.count { it.isLetter() }
        if (lettersCount < binaryFull.length) {
            emitEffect(CaseChangeEffect.ShowMessage("Недостатньо літер у контейнері (${lettersCount} < ${binaryFull.length})"))
            return
        }

        var bitIndex = 0
        val result = StringBuilder()
        for (char in container) {
            if (char.isLetter() && bitIndex < binaryFull.length) {
                if (binaryFull[bitIndex] == '1') {
                    result.append(char.uppercaseChar())
                } else {
                    result.append(char.lowercaseChar())
                }
                bitIndex++
            } else {
                result.append(char)
            }
        }

        _state.update { it.copy(outputText = result.toString()) }
        emitEffect(CaseChangeEffect.ShowMessage("Успішно приховано"))
    }

    private fun decodeText() {
        val st = _state.value
        val encodedText = st.outputText.ifEmpty { st.containerText }

        val binaryBuilder = StringBuilder()
        for (char in encodedText) {
            if (char.isLetter()) {
                if (char.isUpperCase()) binaryBuilder.append('1')
                else binaryBuilder.append('0')
            }
        }

        if (binaryBuilder.isEmpty()) {
            emitEffect(CaseChangeEffect.ShowMessage("Прихованого повідомлення не знайдено"))
            return
        }

        val cleanLength = binaryBuilder.length - (binaryBuilder.length % 8)
        val validBinary = binaryBuilder.substring(0, cleanLength)

        if (validBinary.isEmpty()) {
            emitEffect(CaseChangeEffect.ShowMessage("Прихованого повідомлення не знайдено"))
            return
        }

        try {
            val bytesList = mutableListOf<Byte>()
            val chunks = validBinary.chunked(8)
            for (chunk in chunks) {
                val byteVal = chunk.toInt(2).toByte()
                if (byteVal == 0.toByte()) {
                    break
                }
                bytesList.add(byteVal)
            }

            val decodedSecretBytes = String(bytesList.toByteArray(), Charsets.UTF_8)
            val decodedSecret = if (st.useGamma) {
                if (st.gammaKey.isEmpty()) {
                    emitEffect(CaseChangeEffect.ShowMessage("Для декодування потрібен ключ"))
                    return
                }
                performGammaArithmetic(decodedSecretBytes, st.gammaKey, st.gammaLanguage, st.gammaFormula, false)
            } else {
                decodedSecretBytes
            }
            _state.update { it.copy(secretText = decodedSecret) }
            emitEffect(CaseChangeEffect.ShowMessage("Успішно декодовано"))
        } catch (e: Exception) {
            emitEffect(CaseChangeEffect.ShowMessage("Помилка декодування"))
        }
    }

    private fun emitEffect(effect: CaseChangeEffect) {
        coroutineScope.launch { _effect.emit(effect) }
    }
}