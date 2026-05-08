package com.bbm.lab3steg.audio

import com.bbm.lab3steg.mvi.Store
import com.bbm.lab3steg.mvi.ViewEffect
import com.bbm.lab3steg.mvi.ViewIntent
import com.bbm.lab3steg.mvi.ViewState
import com.bbm.lab3steg.utils.performGammaArithmetic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AudioState(
    val audioFileName: String = "",
    val secretText: String = "",
    val originalBytes: ByteArray? = null,
    val encodedBytes: ByteArray? = null,
    val useGamma: Boolean = false,
    val gammaKey: String = "",
    val gammaLanguage: String = "Ukrainian",
    val gammaFormula: String = "S = Г + О",
    val stegoKey: String = ""
) : ViewState

sealed class AudioIntent : ViewIntent {
    data class LoadAudio(val fileName: String, val bytes: ByteArray) : AudioIntent()
    data class UpdateSecretText(val text: String) : AudioIntent()
    data class UpdateUseGamma(val use: Boolean) : AudioIntent()
    data class UpdateGammaKey(val text: String) : AudioIntent()
    data class UpdateStegoKey(val text: String) : AudioIntent()
    object Encode : AudioIntent()
    object Decode : AudioIntent()
}

sealed class AudioEffect : ViewEffect {
    data class ShowMessage(val message: String) : AudioEffect()
}

class AudioStore(private val coroutineScope: CoroutineScope) : Store<AudioState, AudioIntent, AudioEffect> {
    private val _state = MutableStateFlow(AudioState())
    override val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<AudioEffect>()
    override val effect = _effect.asSharedFlow()

    override fun sendIntent(intent: AudioIntent) {
        when (intent) {
            is AudioIntent.LoadAudio -> _state.update { it.copy(audioFileName = intent.fileName, originalBytes = intent.bytes, encodedBytes = null) }
            is AudioIntent.UpdateSecretText -> _state.update { it.copy(secretText = intent.text) }
            is AudioIntent.UpdateUseGamma -> _state.update { it.copy(useGamma = intent.use) }
            is AudioIntent.UpdateGammaKey -> _state.update { it.copy(gammaKey = intent.text) }
            is AudioIntent.UpdateStegoKey -> _state.update { it.copy(stegoKey = intent.text) }
            AudioIntent.Encode -> encodeText()
            AudioIntent.Decode -> decodeText()
        }
    }

    private fun getAudioDataOffset(bytes: ByteArray): Int {
        // Шукаємо маркер початку "data" (0x64, 0x61, 0x74, 0x61)
        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == 0x64.toByte() && bytes[i + 1] == 0x61.toByte() &&
                bytes[i + 2] == 0x74.toByte() && bytes[i + 3] == 0x61.toByte()
            ) {
                // Після слова "data" йде 4 байти розміру, тому аудіодані починаються через 8 байтів
                return i + 8
            }
        }
        return 44
    }

    private fun encodeText() {
        val st = _state.value
        val originalBytes = st.originalBytes
        val originalSecret = st.secretText

        if (originalBytes == null) {
            emitEffect(AudioEffect.ShowMessage("Будь ласка, завантажте WAV файл"))
            return
        }
        if (originalSecret.isEmpty()) {
            emitEffect(AudioEffect.ShowMessage("Секретне повідомлення порожнє"))
            return
        }
        if (st.stegoKey.isEmpty()) {
            emitEffect(AudioEffect.ShowMessage("Введіть ключ для псевдовипадкового розподілу (LSB)"))
            return
        }

        val secret = if (st.useGamma) {
            if (st.gammaKey.isEmpty()) {
                emitEffect(AudioEffect.ShowMessage("Ключ порожній"))
                return
            }
            performGammaArithmetic(originalSecret, st.gammaKey, st.gammaLanguage, st.gammaFormula, true)
        } else originalSecret

        val binarySecret = secret.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            byte.toUByte().toString(2).padStart(8, '0')
        }
        val binaryFull = binarySecret + "00000000"

        val headerSize = getAudioDataOffset(originalBytes)

        val seed = st.stegoKey.fold(0L) { acc, char -> acc * 31 + char.code }
        val random = kotlin.random.Random(seed)
        val availableIndices = (headerSize until originalBytes.size).shuffled(random)

        if (availableIndices.size < binaryFull.length) {
            emitEffect(AudioEffect.ShowMessage("Файл замалий для цього повідомлення"))
            return
        }

        val newBytes = originalBytes.clone()
        for (i in binaryFull.indices) {
            val bit = if (binaryFull[i] == '1') 1 else 0
            val bytePos = availableIndices[i]
            newBytes[bytePos] = ((newBytes[bytePos].toInt() and 0xFE) or bit).toByte()
        }

        _state.update { it.copy(encodedBytes = newBytes) }
        emitEffect(AudioEffect.ShowMessage("Успішно приховано. Можна зберігати файл."))
    }

    private fun decodeText() {
        val st = _state.value
        val targetBytes = st.encodedBytes ?: st.originalBytes

        if (targetBytes == null) {
            emitEffect(AudioEffect.ShowMessage("Відкрийте WAV файл для декодування"))
            return
        }
        if (st.stegoKey.isEmpty()) {
            emitEffect(AudioEffect.ShowMessage("Введіть ключ для псевдовипадкового витягування (LSB)"))
            return
        }

        val headerSize = getAudioDataOffset(targetBytes)

        val seed = st.stegoKey.fold(0L) { acc, char -> acc * 31 + char.code }
        val random = kotlin.random.Random(seed)
        val availableIndices = (headerSize until targetBytes.size).shuffled(random)

        val bytesList = mutableListOf<Byte>()
        var currentByte = 0
        var bitCount = 0

        for (i in availableIndices.indices) {
            val bytePos = availableIndices[i]
            val bit = targetBytes[bytePos].toInt() and 1
            currentByte = (currentByte shl 1) or bit
            bitCount++

            if (bitCount == 8) {
                if (currentByte == 0) break
                bytesList.add(currentByte.toByte())
                currentByte = 0
                bitCount = 0
            }
        }

        try {
            val decodedSecretBytes = String(bytesList.toByteArray(), Charsets.UTF_8)
            val decodedSecret = if (st.useGamma) {
                if (st.gammaKey.isEmpty()) {
                    emitEffect(AudioEffect.ShowMessage("Для декодування потрібен ключ"))
                    return
                }
                performGammaArithmetic(decodedSecretBytes, st.gammaKey, st.gammaLanguage, st.gammaFormula, false)
            } else decodedSecretBytes

            _state.update { it.copy(secretText = decodedSecret) }
            emitEffect(AudioEffect.ShowMessage("Декодовано повідомлення"))
        } catch (e: Exception) {
            emitEffect(AudioEffect.ShowMessage("Помилка при декодуванні"))
        }
    }

    private fun emitEffect(effect: AudioEffect) {
        coroutineScope.launch { _effect.emit(effect) }
    }
}
