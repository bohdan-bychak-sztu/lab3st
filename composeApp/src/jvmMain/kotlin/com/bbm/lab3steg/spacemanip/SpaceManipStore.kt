package com.bbm.lab3steg.spacemanip

import com.bbm.lab3steg.mvi.Store
import com.bbm.lab3steg.mvi.ViewEffect
import com.bbm.lab3steg.mvi.ViewIntent
import com.bbm.lab3steg.mvi.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bbm.lab3steg.utils.performGammaArithmetic

data class SpaceManipState(
    val containerText: String = "",
    val secretText: String = "",
    val outputText: String = "",
    val useGamma: Boolean = false,
    val gammaKey: String = "",
    val gammaLanguage: String = "Ukrainian",
    val gammaFormula: String = "S = Г + О"
) : ViewState

sealed class SpaceManipIntent : ViewIntent {
    data class UpdateContainerText(val text: String) : SpaceManipIntent()
    data class UpdateSecretText(val text: String) : SpaceManipIntent()
    data class UpdateOutputText(val text: String) : SpaceManipIntent()
    data class UpdateUseGamma(val use: Boolean) : SpaceManipIntent()
    data class UpdateGammaKey(val text: String) : SpaceManipIntent()
    object Encode : SpaceManipIntent()
    object Decode : SpaceManipIntent()
}

sealed class SpaceManipEffect : ViewEffect {
    data class ShowMessage(val message: String) : SpaceManipEffect()
}

class SpaceManipStore(private val coroutineScope: CoroutineScope) : Store<SpaceManipState, SpaceManipIntent, SpaceManipEffect> {
    private val _state = MutableStateFlow(SpaceManipState())
    override val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SpaceManipEffect>()
    override val effect = _effect.asSharedFlow()

    override fun sendIntent(intent: SpaceManipIntent) {
        when (intent) {
            is SpaceManipIntent.UpdateContainerText -> _state.update { it.copy(containerText = intent.text) }
            is SpaceManipIntent.UpdateSecretText -> _state.update { it.copy(secretText = intent.text) }
            is SpaceManipIntent.UpdateOutputText -> _state.update { it.copy(outputText = intent.text) }
            is SpaceManipIntent.UpdateUseGamma -> _state.update { it.copy(useGamma = intent.use) }
            is SpaceManipIntent.UpdateGammaKey -> _state.update { it.copy(gammaKey = intent.text) }
            SpaceManipIntent.Encode -> encodeText()
            SpaceManipIntent.Decode -> decodeText()
        }
    }

    private fun encodeText() {
        val st = _state.value
        val container = st.containerText
        val originalSecret = st.secretText

        if (container.isEmpty() || originalSecret.isEmpty()) {
            emitEffect(SpaceManipEffect.ShowMessage("Контейнер або повідомлення порожні"))
            return
        }

        val secret = if (st.useGamma) {
            if (st.gammaKey.isEmpty()) {
                emitEffect(SpaceManipEffect.ShowMessage("Ключ гамування порожній"))
                return
            }
            performGammaArithmetic(originalSecret, st.gammaKey, st.gammaLanguage, st.gammaFormula, true)
        } else originalSecret

        val binarySecret = secret.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            byte.toUByte().toString(2).padStart(8, '0')
        }

        val binaryFull = binarySecret + "00000000"

        val words = container.split(" ")
        if (words.size - 1 < binaryFull.length) {
            emitEffect(SpaceManipEffect.ShowMessage("Недостатньо пробілів у контейнері (${words.size - 1} < ${binaryFull.length})"))
            return
        }

        var bitIndex = 0
        val result = StringBuilder()
        result.append(words[0])

        for (i in 1 until words.size) {
            if (bitIndex < binaryFull.length) {
                if (binaryFull[bitIndex] == '1') {
                    result.append("  ") // Два пробіли для '1'
                } else {
                    result.append(" ")  // Один пробіл для '0'
                }
                bitIndex++
            } else {
                result.append(" ")
            }
            result.append(words[i])
        }

        _state.update { it.copy(outputText = result.toString()) }
        emitEffect(SpaceManipEffect.ShowMessage("Успішно приховано"))
    }

    private fun decodeText() {
        val st = _state.value
        val encodedText = st.outputText.ifEmpty { st.containerText }

        val binaryBuilder = StringBuilder()
        var i = 0
        while (i < encodedText.length) {
            if (encodedText[i] == ' ') {
                if (i + 1 < encodedText.length && encodedText[i + 1] == ' ') {
                    binaryBuilder.append('1')
                    i++
                } else {
                    binaryBuilder.append('0')
                }
            }
            i++
        }

        if (binaryBuilder.isEmpty()) {
            emitEffect(SpaceManipEffect.ShowMessage("Прихованого повідомлення не знайдено"))
            return
        }

        val cleanLength = binaryBuilder.length - (binaryBuilder.length % 8)
        val validBinary = binaryBuilder.substring(0, cleanLength)

        if (validBinary.isEmpty()) {
            emitEffect(SpaceManipEffect.ShowMessage("Прихованого повідомлення не знайдено"))
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
                    emitEffect(SpaceManipEffect.ShowMessage("Для декодування потрібен ключ"))
                    return
                }
                performGammaArithmetic(decodedSecretBytes, st.gammaKey, st.gammaLanguage, st.gammaFormula, false)
            } else decodedSecretBytes
            _state.update { it.copy(secretText = decodedSecret) }
            emitEffect(SpaceManipEffect.ShowMessage("Успішно декодовано"))
        } catch (e: Exception) {
            emitEffect(SpaceManipEffect.ShowMessage("Помилка декодування"))
        }
    }

    private fun emitEffect(effect: SpaceManipEffect) {
        coroutineScope.launch { _effect.emit(effect) }
    }
}
