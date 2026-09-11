package com.z3itt.dualis.ml

import android.os.Build
import java.io.File

enum class InferBackend { QNN, NNAPI, CPU }

object InferAccel {
    fun isQualcommSoc(
        hardware: String = Build.HARDWARE,
        board: String = Build.BOARD,
        manufacturer: String = Build.MANUFACTURER,
        socManufacturer: String = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else "",
        socModel: String = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "",
    ): Boolean {
        val blob = "$hardware $board $manufacturer $socManufacturer $socModel".lowercase()
        return blob.contains("qcom") ||
            blob.contains("qualcomm") ||
            blob.contains("snapdragon") ||
            Regex("""\bsm\d{4}\b""").containsMatchIn(blob)
    }

    fun qnnBackendPath(nativeLibDir: String?): String? {
        if (nativeLibDir.isNullOrBlank()) return null
        val lib = File(nativeLibDir, "libQnnHtp.so")
        return lib.takeIf { it.isFile }?.absolutePath
    }

    fun shouldTryQnn(nativeLibDir: String?): Boolean =
        isQualcommSoc() && qnnBackendPath(nativeLibDir) != null
}
