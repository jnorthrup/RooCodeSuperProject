package labelextractor

import GranularityLevel
import astminer.common.splitToSubtokens
import com.intellij.psi.PsiElement
import psi.language.LanguageHandler
import psi.nodeProperties.technicalToken

class MethodNameLabelExtractor : LabelExtractor() {

    override val granularityLevel = GranularityLevel.Method

    override fun handleTree(root: PsiElement, languageHandler: LanguageHandler): Label {
        val methodNameNode = languageHandler.methodProvider.getNameNode(root)
        // Mark all occurrences in subtree with METHOD_NAME token
        methodNameNode.technicalToken = METHOD_NAME
        languageHandler.actionOnRecursiveCallIdentifier(root) {
            it.technicalToken = METHOD_NAME
        }
        return StringLabel(splitToSubtokens(methodNameNode.text).joinToString("|"))
    }

    internal companion object {
        const val METHOD_NAME = "METHOD_NAME"
    }
}
