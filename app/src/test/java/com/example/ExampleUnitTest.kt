package com.example

import com.example.domain.FaceDescriptorEngine
import com.example.domain.MatchEvaluation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class ExampleUnitTest {

    private fun sample192Embedding(seed: Int): FloatArray {
        val raw = FloatArray(192) { i -> sin((i + 1) * (seed + 1) * 0.31).toFloat() }
        return FaceDescriptorEngine.l2Normalize(raw)
    }

    @Test
    fun `evaluateClosestMatch verifies registered student and rejects unknown`() {
        val embA = sample192Embedding(1)
        val embB = sample192Embedding(7)
        val embUnknown = sample192Embedding(19)

        val candidates = listOf("StudentA" to embA, "StudentB" to embB)

        val matchA = FaceDescriptorEngine.evaluateClosestMatch(embA, candidates, 0.72f)
        assertTrue(matchA is MatchEvaluation.VerifiedMatch)
        assertEquals("StudentA", (matchA as MatchEvaluation.VerifiedMatch).candidate)

        val unknownEval = FaceDescriptorEngine.evaluateClosestMatch(embUnknown, candidates, 0.72f)
        assertTrue(unknownEval is MatchEvaluation.UnknownFace)
    }

    @Test
    fun `aggregateMultiAngleEmbeddings computes usable 192D centroid`() {
        val base = sample192Embedding(4)
        val samples = List(5) { idx ->
            FaceDescriptorEngine.l2Normalize(
                FloatArray(192) { i -> base[i] + (idx - 2) * 0.005f }
            )
        }
        val centroid = FaceDescriptorEngine.aggregateMultiAngleEmbeddings(samples)
        assertNotNull(centroid)
        assertEquals(192, centroid!!.size)
        assertTrue(FaceDescriptorEngine.isEmbeddingUsable(centroid))
    }
}
