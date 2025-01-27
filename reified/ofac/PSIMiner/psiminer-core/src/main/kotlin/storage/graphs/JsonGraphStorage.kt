package storage.graphs

import Dataset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import labelextractor.LabeledTree
import psi.graphs.graphMiners.GraphMiner
import storage.Storage
import java.io.File

class JsonGraphStorage(
    outputDirectory: File,
    private val graphMiner: GraphMiner,
) : Storage(outputDirectory) {
    override val fileExtension = "json"

    private val jsonSerializer = Json { encodeDefaults = false }

    private var nodeCount = 0
    private var edgeCount = 0
    private var totalLength = 0

    override fun convert(labeledTree: LabeledTree, holdout: Dataset?): String {
        val codeGraph = graphMiner.mine(labeledTree.root)
        nodeCount += codeGraph.vertices.size
        edgeCount += codeGraph.getAllEdges().size

        val representation = JsonLabeledGraphRepresentation.convertLabeledCodeGraph(
            codeGraph,
            labeledTree.label,
        )
        val output = jsonSerializer.encodeToString(representation)
        totalLength += output.length
        return output
    }

    override fun close() {
        super.close()
        println("$nodeCount nodes")
        println("$edgeCount edges")
        println("$totalLength symbols")
    }
}
