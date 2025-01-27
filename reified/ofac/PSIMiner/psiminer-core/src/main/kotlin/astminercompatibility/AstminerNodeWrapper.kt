import astminer.common.model.Node
import astminercompatibility.nodeRange
import com.intellij.psi.PsiElement
import psi.nodeProperties.isHidden
import psi.nodeProperties.nodeType
import psi.nodeProperties.token

class AstminerNodeWrapper(val psiNode: PsiElement, override val parent: Node? = null) : Node(psiNode.token) {
    override val children: MutableList<AstminerNodeWrapper> by lazy {
        psiNode.children.filter { !it.isHidden }.map { AstminerNodeWrapper(it, this) }.toMutableList()
    }

    override val range = psiNode.nodeRange()

    override val typeLabel: String = psiNode.nodeType

    override fun removeChildrenOfType(typeLabel: String) {
        children.removeIf { it.typeLabel == typeLabel }
    }
}
