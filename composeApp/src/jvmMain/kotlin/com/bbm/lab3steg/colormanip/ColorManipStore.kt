package com.bbm.lab3steg.colormanip

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

data class ColorManipState(
    val containerText: String = "",
    val secretText: String = "",
    val outputText: String = "",
    val useGamma: Boolean = false,
    val gammaKey: String = "",
    val gammaLanguage: String = "Ukrainian",
    val gammaFormula: String = "S = Г + О"
) : ViewState

sealed class ColorManipIntent : ViewIntent {
    data class UpdateContainerText(val text: String) : ColorManipIntent()
    data class UpdateSecretText(val text: String) : ColorManipIntent()
    data class UpdateOutputText(val text: String) : ColorManipIntent()
    data class UpdateUseGamma(val use: Boolean) : ColorManipIntent()
    data class UpdateGammaKey(val text: String) : ColorManipIntent()
    object Encode : ColorManipIntent()
    object Decode : ColorManipIntent()
}

sealed class ColorManipEffect : ViewEffect {
    data class ShowMessage(val message: String) : ColorManipEffect()
}

class ColorManipStore(private val coroutineScope: CoroutineScope) : Store<ColorManipState, ColorManipIntent, ColorManipEffect> {
    private val _state = MutableStateFlow(ColorManipState())
    override val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ColorManipEffect>()
    override val effect = _effect.asSharedFlow()

    override fun sendIntent(intent: ColorManipIntent) {
        when (intent) {
            is ColorManipIntent.UpdateContainerText -> _state.update { it.copy(containerText = intent.text) }
            is ColorManipIntent.UpdateSecretText -> _state.update { it.copy(secretText = intent.text) }
            is ColorManipIntent.UpdateOutputText -> _state.update { it.copy(outputText = intent.text) }
            is ColorManipIntent.UpdateUseGamma -> _state.update { it.copy(useGamma = intent.use) }
            is ColorManipIntent.UpdateGammaKey -> _state.update { it.copy(gammaKey = intent.text) }
            ColorManipIntent.Encode -> encodeText()
            ColorManipIntent.Decode -> decodeText()
        }
    }

    private fun encodeText() {
        val st = _state.value
        val container = st.containerText
        val originalSecret = st.secretText

        if (container.isEmpty() || originalSecret.isEmpty()) {
            emitEffect(ColorManipEffect.ShowMessage("Контейнер або повідомлення порожні"))
            return
        }

        val secret = if (st.useGamma) {
            if (st.gammaKey.isEmpty()) {
                emitEffect(ColorManipEffect.ShowMessage("Ключ порожній"))
                return
            }
            performGammaArithmetic(originalSecret, st.gammaKey, st.gammaLanguage, st.gammaFormula, true)
        } else originalSecret

        val binarySecret = secret.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            byte.toUByte().toString(2).padStart(8, '0')
        }

        val binaryFull = binarySecret + "00000000"

        if (container.length < binaryFull.length) {
            emitEffect(ColorManipEffect.ShowMessage("Недостатньо символів у контейнері (${container.length} < ${binaryFull.length})"))
            return
        }

        val resultHtml = StringBuilder()
        var bitIndex = 0

        for (i in container.indices) {
            val char = container[i]
            if (i < binaryFull.length) {
                val bit = binaryFull[i]
                val color = if (bit == '1') "#000001" else "#000000"
                resultHtml.append("<span style=\"color:$color\">$char</span>")
                bitIndex++
            } else {
                resultHtml.append(char)
            }
        }

        _state.update { it.copy(outputText = resultHtml.toString()) }
        emitEffect(ColorManipEffect.ShowMessage("Успішно приховано (результат в HTML)"))
    }

    private fun decodeText() {
        val st = _state.value
        val encodedText = st.outputText

        if (encodedText.isEmpty()) {
            emitEffect(ColorManipEffect.ShowMessage("Результат порожній"))
            return
        }

        // <span style="color:#000000">T</span>
        val colorRegex = Regex("""color:(#[0-9a-fA-F]{6})""")
        val matches = colorRegex.findAll(encodedText).toList()

        val binaryBuilder = StringBuilder()
        for (match in matches) {
            val color = match.groupValues[1].lowercase()
            if (color == "#000001") {
                binaryBuilder.append('1')
            } else if (color == "#000000") {
                binaryBuilder.append('0')
            }
        }

        if (binaryBuilder.isEmpty()) {
            emitEffect(ColorManipEffect.ShowMessage("Слідів зміни кольору не знайдено"))
            return
        }

        val cleanLength = binaryBuilder.length - (binaryBuilder.length % 8)
        val validBinary = binaryBuilder.substring(0, cleanLength)

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

            if (bytesList.isEmpty()) {
                emitEffect(ColorManipEffect.ShowMessage("Прихованого повідомлення не знайдено"))
                return
            }

            val decodedSecretBytes = String(bytesList.toByteArray(), Charsets.UTF_8)
            val decodedSecret = if (st.useGamma) {
                if (st.gammaKey.isEmpty()) {
                    emitEffect(ColorManipEffect.ShowMessage("Для декодування потрібен ключ"))
                    return
                }
                performGammaArithmetic(decodedSecretBytes, st.gammaKey, st.gammaLanguage, st.gammaFormula, false)
            } else decodedSecretBytes

            _state.update { it.copy(secretText = decodedSecret) }
            emitEffect(ColorManipEffect.ShowMessage("Успішно декодовано з HTML"))
        } catch (e: Exception) {
            emitEffect(ColorManipEffect.ShowMessage("Помилка декодування"))
        }
    }

    private fun emitEffect(effect: ColorManipEffect) {
        coroutineScope.launch { _effect.emit(effect) }
    }
}
