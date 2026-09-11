package com.z3itt.dualis

import com.z3itt.dualis.ml.InferAccel
import com.z3itt.dualis.ml.ModelCatalog
import com.z3itt.dualis.ml.PrimaryStem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferAccelTest {
    @Test
    fun detectsSnapdragonEvenOnSamsung() {
        assertTrue(
            InferAccel.isQualcommSoc(
                hardware = "qcom",
                board = "kalama",
                manufacturer = "samsung",
                socManufacturer = "Qualcomm",
                socModel = "SM8550",
            ),
        )
    }

    @Test
    fun skipsTensorAndExynos() {
        assertFalse(
            InferAccel.isQualcommSoc(
                hardware = "tensor",
                board = "bluejay",
                manufacturer = "Google",
                socManufacturer = "Google",
                socModel = "Tensor G2",
            ),
        )
    }

    @Test
    fun kimVocal2StaysDefaultAndKaraoke2IsOptional() {
        assertEquals("kim-vocal-2", ModelCatalog.DEFAULT_MODEL_ID)
        assertEquals(0.25f, ModelCatalog.require("kim-vocal-2").config.overlap)
        assertEquals(0.25f, ModelCatalog.require("uvr-mdx-kara-2").config.overlap)
        assertEquals(PrimaryStem.INSTRUMENTAL, ModelCatalog.require("uvr-mdx-kara-2").config.primaryStem)
        assertEquals(PrimaryStem.VOCALS, ModelCatalog.require("kim-vocal-2").config.primaryStem)
        assertTrue(ModelCatalog.catalog.any { it.id == "uvr-mdx-kara-2" })
    }
}
