package psi.graphs.edgeProviders.java

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.controlFlow.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.slf4j.LoggerFactory
import psi.graphs.CodeGraph
import psi.graphs.Edge
import psi.graphs.EdgeType
import psi.graphs.edgeProviders.EdgeProvider
import psi.methodRoot

class JavaControlFlowEdgeProvider : EdgeProvider(
    dependsOn = setOf(EdgeType.Ast),
    providedType = EdgeType.ControlFlow
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val PsiElement.controlFlow: ControlFlow
        get() = ControlFlowFactory.getInstance(project).getControlFlow(
            this,
            AllVariablesControlFlowPolicy.getInstance()
        )

    private fun getPsiElementFromControlFlow(controlFlow: ControlFlow, index: Int): PsiElement {
        val instruction = controlFlow.instructions[index]
        val element = controlFlow.getElement(index)
        val variable = when (instruction) {
            is ReadVariableInstruction -> instruction.variable
            is WriteVariableInstruction -> instruction.variable
            else -> null
        }
        return if (variable != null && variable.parents.contains(element)) {
            variable
        } else {
            element
        }
    }

    private fun computeEdgeType(instruction: Instruction, hasNextInstruction: Boolean) = when (instruction) {
        is GoToInstruction -> if (instruction.isReturn || !hasNextInstruction) {
            EdgeType.ReturnsTo
        } else {
            EdgeType.ControlElement
        }

        is ThrowToInstruction -> EdgeType.ThrowsTo
        else -> if (hasNextInstruction) {
            EdgeType.ControlElement
        } else {
            EdgeType.ReturnsTo
        }
    }

    private fun computeNextNonBranchingInstructions(instructions: List<Instruction>): Array<out Set<Int>> {
        val n = instructions.size
        val nextInstructions = Array<MutableSet<Int>>(n) { mutableSetOf() }
        val used = BooleanArray(n) { false }

        fun dfsFromInstruction(index: Int) {
            if (used[index]) {
                return
            }
            used[index] = true
            val instruction = instructions[index]
            for (no in 0 until instruction.nNext()) {
                val toIndex = instruction.getNext(index, no)
                if (toIndex < n) {
                    val toInstruction = instructions[toIndex]
                    if (toInstruction !is BranchingInstruction) {
                        nextInstructions[index].add(toIndex)
                    } else {
                        dfsFromInstruction(toIndex)
                        nextInstructions[index].addAll(nextInstructions[toIndex])
                    }
                }
            }
        }

        for (index in 0 until n) {
            dfsFromInstruction(index)
        }
        return nextInstructions
    }

    private fun provideEdgesForInstruction(
        index: Int,
        instructions: List<Instruction>,
        elements: List<PsiElement>,
        nextInstructions: Array<out Set<Int>>,
        newEdges: MutableList<Edge>,
    ) {
        val instruction = instructions[index]
        val element = elements[index]
        if (instruction !is BranchingInstruction) {
            nextInstructions[index].forEach { toIndex ->
                val toElement = elements[toIndex]
                if (toElement != element) {
                    newEdges.add(Edge(element, toElement, EdgeType.ControlFlow))
                }
            }
        }
        for (no in 0 until instruction.nNext()) {
            val toIndex = instruction.getNext(index, no)
            val hasNextInstruction = toIndex < instructions.size
            val edgeType = computeEdgeType(instruction, hasNextInstruction)
            if (hasNextInstruction) {
                val toInstruction = instructions[toIndex]
                val toElement = elements[toIndex]
                if (
                    element != toElement &&
                    (instruction is BranchingInstruction || toInstruction is BranchingInstruction)
                ) {
                    newEdges.add(Edge(element, toElement, edgeType))
                }
            } else {
                val methodRoot = element.methodRoot()
                if (methodRoot != null) {
                    newEdges.add(Edge(element, methodRoot, edgeType))
                }
            }
        }
    }

    private fun provideEdgesForMethod(vertex: PsiMethod, newEdges: MutableList<Edge>) {
        val controlFlow = try {
            vertex.body?.controlFlow ?: return
        } catch (e: AnalysisCanceledException) {
            logger.warn("Ignored method due to exception in control flow construction: ${e.message}")
            return
        }
        val instructions = controlFlow.instructions
        val elements = (0 until instructions.size).map { index -> getPsiElementFromControlFlow(controlFlow, index) }
        val nextInstructions = computeNextNonBranchingInstructions(instructions)
        (0 until instructions.size).forEach { index ->
            provideEdgesForInstruction(index, instructions, elements, nextInstructions, newEdges)
        }
    }

    override fun provideEdges(graph: CodeGraph): List<Edge> {
        val newEdges = mutableListOf<Edge>()
        graph.vertices.filterIsInstance<PsiMethod>().forEach { vertex ->
            provideEdgesForMethod(vertex, newEdges)
        }
        return newEdges
    }
}
