package org.jetbrains.research.ml.ast.transformations.deadcode

import org.jetbrains.research.ml.ast.transformations.util.TransformationsTest
import org.jetbrains.research.ml.ast.transformations.util.TransformationsTestHelper.getBackwardTransformationWrapper
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DeadCodeRemovalTransformationTest : TransformationsTest(getResourcesRootPath(::TransformationsTest)) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: ({0}, {1})")
        fun getTestData() = getInAndOutArray(::DeadCodeRemovalTransformationTest)
    }

    @Test
    fun testForwardTransformation() {
        assertCodeTransformation(
            inFile!!,
            outFile!!,
            DeadCodeRemovalTransformation::forwardApply
        )
    }

    @Test
    fun testBackwardTransformation() {
        assertCodeTransformation(
            inFile!!,
            inFile!!,
            getBackwardTransformationWrapper(DeadCodeRemovalTransformation::forwardApply)
        )
    }
}
