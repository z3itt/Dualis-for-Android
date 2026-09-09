package com.z3itt.dualis.ml

enum class Architecture { MDX, ROFORMER }

enum class WaveLayout { CHANNEL_FIRST, CHANNEL_LAST }

data class ModelConfig(
    val nFft: Int = 6144,
    val hop: Int = 1024,
    val dimF: Int = 3072,
    val dimT: Int = 256,
    val compensate: Float = 1.035f,
    val overlap: Float = 0.25f,
    val waveformLen: Int = 0,
    val layout: WaveLayout = WaveLayout.CHANNEL_FIRST,
) {
    fun chunkSize(): Int = hop * (dimT - 1).coerceAtLeast(0)
}

data class ModelSpec(
    val id: String,
    val name: String,
    val filename: String,
    val urls: List<String>,
    val architecture: Architecture,
    val config: ModelConfig,
    val description: String,
)

object ModelCatalog {
    const val DEFAULT_MODEL_ID = "kim-vocal-2"

    val catalog: List<ModelSpec> = listOf(
        ModelSpec(
            id = "kim-vocal-2",
            name = "Kim Vocal 2",
            filename = "Kim_Vocal_2.onnx",
            urls = listOf(
                "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/Kim_Vocal_2.onnx",
                "https://huggingface.co/seanghay/uvr_models/resolve/main/Kim_Vocal_2.onnx",
            ),
            architecture = Architecture.MDX,
            config = ModelConfig(
                nFft = 6144,
                hop = 1024,
                dimF = 3072,
                dimT = 256,
                compensate = 1.035f,
                overlap = 0.25f,
            ),
            description = "MDX-Net vocal model. Fast, reliable default for pop and electronic.",
        ),
        ModelSpec(
            id = "uvr-mdx-voc-ft",
            name = "UVR MDX Voc FT",
            filename = "UVR-MDX-NET-Voc_FT.onnx",
            urls = listOf(
                "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR-MDX-NET-Voc_FT.onnx",
                "https://huggingface.co/seanghay/uvr_models/resolve/main/UVR-MDX-NET-Voc_FT.onnx",
            ),
            architecture = Architecture.MDX,
            config = ModelConfig(
                nFft = 7680,
                hop = 1024,
                dimF = 3072,
                dimT = 256,
                compensate = 1.0f,
                overlap = 0.25f,
            ),
            description = "Fine-tuned MDX vocal net. Different timbre, same spectrogram pipeline.",
        ),
        ModelSpec(
            id = "bs-roformer",
            name = "BS-Roformer",
            filename = "model_bs_roformer.onnx",
            urls = listOf(
                "https://huggingface.co/seanghay/uvr_models/resolve/main/model_bs_roformer_ep_317_sdr_12.9755.onnx",
            ),
            architecture = Architecture.ROFORMER,
            config = ModelConfig(
                nFft = 2048,
                hop = 441,
                dimF = 1024,
                dimT = 256,
                compensate = 1.0f,
                overlap = 0.25f,
                waveformLen = 352_800,
            ),
            description = "Roformer-style waveform ONNX. Shape is read from the file at load time.",
        ),
    )

    fun byId(id: String): ModelSpec? = catalog.find { it.id == id }

    fun require(id: String): ModelSpec = byId(id) ?: byId(DEFAULT_MODEL_ID)!!
}
