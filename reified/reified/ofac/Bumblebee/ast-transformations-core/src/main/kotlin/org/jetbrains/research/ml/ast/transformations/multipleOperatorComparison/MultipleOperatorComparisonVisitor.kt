package org.jetbrains.research.ml.ast.transformations.multipleOperatorComparison

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyElementVisitor
import org.jetbrains.research.ml.ast.transformations.PerformedCommandStorage
import org.jetbrains.research.ml.ast.transformations.PyUtils
import org.jetbrains.research.ml.ast.transformations.safePerformCommand

internal class MultipleOperatorComparisonVisitor(private val commandsStorage: PerformedCommandStorage?) :
    PyElementVisitor() {
    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
        handleBinaryExpression(node)
        super.visitPyBinaryExpression(node)
    }

    private fun handleBinaryExpression(node: PyBinaryExpression) {
        if (!node.isMultipleOperatorComparison()) {
            return
        }

        val generator = PyElementGenerator.getInstance(node.project)
        val newBinaryExpression = transformMultipleComparisonExpression(node, generator) ?: return
        val newBracedExpression = PyUtils.braceExpression(newBinaryExpression)
        commandsStorage.safePerformCommand(
            { node.replace(newBracedExpression) },
            "Replace multiple operation comparison with braced expression"
        )
    }

    private fun transformMultipleComparisonExpression(
        node: PyBinaryExpression,
        generator: PyElementGenerator
    ): PyBinaryExpression? {
        if (!node.isMultipleOperatorComparison()) {
            return null
        }

        val leftBinaryExpression = node.leftExpression as PyBinaryExpression
        val leftRightExpression = leftBinaryExpression.rightExpression ?: return null
        val rightExpression = node.rightExpression ?: return null
        val nodeOperator = node.psiOperator ?: return null

        val newRightExpression = generator.createBinaryExpression(
            nodeOperator.text,
            leftRightExpression,
            rightExpression
        )

        val newLeftBinaryExpression = transformMultipleComparisonExpression(
            leftBinaryExpression,
            generator
        ) ?: leftBinaryExpression

        return generator.createBinaryExpression("and", newLeftBinaryExpression, newRightExpression)
    }

    private fun PyBinaryExpression.isComparison(): Boolean = PyTokenTypes.COMPARISON_OPERATIONS.contains(operator)

    private fun PyBinaryExpression.isMultipleOperatorComparison(): Boolean {
        when (operator) {
            PyTokenTypes.AND_KEYWORD, PyTokenTypes.OR_KEYWORD -> return false
            else -> {
                val leftBinaryExpression = leftExpression as? PyBinaryExpression ?: return false
                return leftBinaryExpression.isComparison() && this.isComparison()
            }
        }
    }
}
