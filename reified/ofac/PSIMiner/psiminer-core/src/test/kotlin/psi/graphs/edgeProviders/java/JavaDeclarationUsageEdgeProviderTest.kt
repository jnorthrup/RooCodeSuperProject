package psi.graphs.edgeProviders.java

import JavaPsiRequiredTest
import com.intellij.openapi.application.ReadAction
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import psi.graphs.EdgeType
import psi.graphs.graphMiners.JavaGraphMiner

internal class JavaDeclarationUsageEdgeProviderTest : JavaPsiRequiredTest("JavaFlowMethods") {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "straightWriteMethod",
            "ifMethod",
            "straightReadWriteMethod",
            "multipleDeclarations",
        ]
    )
    fun `test data flow extraction from Java methods`(methodName: String) {
        val psiRoot = getMethod(methodName)
        val graphMiner = JavaGraphMiner()
        ReadAction.run<Exception> {
            val codeGraph = graphMiner.mine(psiRoot)
            val declarationUsageEdges = codeGraph.getAllEdges().filter {
                it.type == EdgeType.DeclarationUsage && !it.reversed
            }
            val textRepresentation = declarationUsageEdges.groupBy {
                it.from.text
            }.map { (varDecl, edges) ->
                varDecl to edges.size
            }.toMap()

            assertEquals(correctDeclCounts[methodName], textRepresentation)
        }
    }

    companion object {
        val correctDeclCounts: Map<String, Map<String, Int>> = mapOf(
            "straightWriteMethod" to mapOf(
                "int a = 1;" to 1,
                "int b = 2;" to 1,
                "int c = 3;" to 1,
                "int d = 4;" to 1,
            ),
            "straightReadWriteMethod" to mapOf(
                "int a = 1;" to 4,
                "int b = a;" to 3,
                "int c = a + b;" to 3,
                "int d = c * c;" to 1,
            ),
            "ifMethod" to mapOf(
                "int a = 1;" to 3,
                "int b = 2;" to 1,
                "int c = 3;" to 1,
                "int d = 4;" to 1,
                "int e = 5;" to 1,
            ),
            "multipleDeclarations" to mapOf(
                "int a = 1;" to 1,
                "int a = 2;" to 4,
                "int a = 3;" to 2,
            ),
        )
    }
}
