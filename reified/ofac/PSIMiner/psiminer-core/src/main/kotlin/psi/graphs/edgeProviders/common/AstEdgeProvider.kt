package psi.graphs.edgeProviders.common

import psi.graphs.CodeGraph
import psi.graphs.Edge
import psi.graphs.EdgeType
import psi.graphs.edgeProviders.EdgeProvider

class AstEdgeProvider : EdgeProvider(dependsOn = emptySet(), providedType = EdgeType.Ast) {
    override fun provideEdges(graph: CodeGraph): List<Edge> =
        graph.vertices.flatMap { from ->
            from.children.map { to ->
                Edge(from, to, providedType)
            }
        }
}
