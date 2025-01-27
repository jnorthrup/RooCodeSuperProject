package com.intellij.ml.llm.template.telemetry

import com.google.gson.annotations.SerializedName
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EfCandidateType
import com.intellij.ml.llm.template.utils.EFApplicationResult
import com.intellij.ml.llm.template.utils.EFCandidateApplicationPayload
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import java.util.*

data class EFTelemetryData(
    @SerializedName("id")
    var id: String,
) {
    @SerializedName("hostFunctionTelemetryData")
    lateinit var hostFunctionTelemetryData: EFHostFunctionTelemetryData

    @SerializedName("candidatesTelemetryData")
    lateinit var candidatesTelemetryData: EFCandidatesTelemetryData

    @SerializedName("userSelectionTelemetryData")
    lateinit var userSelectionTelemetryData: EFUserSelectionTelemetryData

    @SerializedName("elapsedTime")
    lateinit var elapsedTime: List<CandidateElapsedTimeTelemetryData>

    @SerializedName("processingTime")
    lateinit var processingTime: EFTelemetryDataProcessingTime
}

data class EFHostFunctionTelemetryData(
    @SerializedName("hostFunctionSize")
    var hostFunctionSize: Int,

    @SerializedName("lineStart")
    var lineStart: Int,

    @SerializedName("lineEnd")
    var lineEnd: Int,

    @SerializedName("bodyLineStart")
    var bodyLineStart: Int,

    @SerializedName("language")
    var language: String
)

data class EFCandidatesTelemetryData(
    @SerializedName("numberOfSuggestions")
    var numberOfSuggestions: Int,

    @SerializedName("candidates")
    var candidates: List<EFCandidateTelemetryData>,
)

data class EFCandidateTelemetryData(
    @SerializedName("lineStart")
    var lineStart: Int,

    @SerializedName("lineEnd")
    var lineEnd: Int,

    @SerializedName("candidateType")
    var candidateType: EfCandidateType,

    @SerializedName("applicationResult")
    var applicationResult: EFApplicationResult,

    @SerializedName("reason")
    var reason: String
)

data class EFUserSelectionTelemetryData(
    @SerializedName("lineStart")
    var lineStart: Int,

    @SerializedName("lineEnd")
    var lineEnd: Int,

    @SerializedName("functionSize")
    var functionSize: Int,

    @SerializedName("positionInHostFunction")
    var positionInHostFunction: Int,

    @SerializedName("selectedCandidateIndex")
    var selectedCandidateIndex: Int,

    @SerializedName("candidateType")
    var candidateType: EfCandidateType,

    @SerializedName("elementsType")
    var elementsType: List<EFPsiElementsTypesTelemetryData>,
)

data class CandidateElapsedTimeTelemetryData(
    @SerializedName("candidateIndex")
    var candidateIndex: Int,

    @SerializedName("elapsedTime")
    var elapsedTime: Long,
)

data class EFPsiElementsTypesTelemetryData(
    @SerializedName("elementType")
    var type: String,

    @SerializedName("quantity")
    var quantity: Int,
)


enum class TelemetryDataAction() {
    START,
    STOP
}

data class EFTelemetryDataElapsedTimeNotificationPayload(
    @SerializedName("action")
    var action: TelemetryDataAction,

    @SerializedName("currentSelectionIndex")
    var selectionIndex: Int
)

data class EFTelemetryDataProcessingTime(
    @SerializedName("llmResponseTime")
    var llmResponseTime: Long,

    @SerializedName("pluginProcessingTime")
    var pluginProcessingTime: Long,

    @SerializedName("totalTime")
    var totalTime: Long
)

class EFTelemetryDataManager {
    private var currentSessionId: String = ""
    private val data: MutableMap<String, EFTelemetryData> = mutableMapOf()
    private lateinit var currentTelemetryData: EFTelemetryData

    fun newSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        currentTelemetryData = EFTelemetryData(currentSessionId)
        data[currentSessionId] = currentTelemetryData
        return currentSessionId
    }

    fun currentSession(): String {
        if (currentSessionId.isNotEmpty()) return currentSessionId
        return newSession()
    }

    fun addHostFunctionTelemetryData(hostFunctionTelemetryData: EFHostFunctionTelemetryData): EFTelemetryDataManager {
        currentTelemetryData.hostFunctionTelemetryData = hostFunctionTelemetryData
        return this
    }

    fun addCandidatesTelemetryData(candidatesTelemetryData: EFCandidatesTelemetryData): EFTelemetryDataManager {
        currentTelemetryData.candidatesTelemetryData = candidatesTelemetryData
        return this
    }

    fun addUserSelectionTelemetryData(userSelectionTelemetryData: EFUserSelectionTelemetryData): EFTelemetryDataManager {
        currentTelemetryData.userSelectionTelemetryData = userSelectionTelemetryData
        return this
    }

    fun getData(sessionId: String? = null): EFTelemetryData? {
        val sId = sessionId ?: currentSession()
        return data.getOrDefault(sId, null)
    }
}

class EFTelemetryDataUtils {
    companion object {
        fun buildHostFunctionTelemetryData(
            codeSnippet: String,
            lineStart: Int,
            bodyLineStart: Int,
            language: String
        ): EFHostFunctionTelemetryData {
            val functionSize = codeSnippet.lines().size
            return EFHostFunctionTelemetryData(
                lineStart = lineStart,
                lineEnd = lineStart + functionSize - 1,
                hostFunctionSize = functionSize,
                bodyLineStart = bodyLineStart,
                language = language
            )
        }

        private fun buildCandidateTelemetryData(candidateApplicationPayload: EFCandidateApplicationPayload): EFCandidateTelemetryData {
            val candidate = candidateApplicationPayload.candidate
            return EFCandidateTelemetryData(
                lineStart = candidate.lineStart,
                lineEnd = candidate.lineEnd,
                candidateType = candidate.type,
                applicationResult = candidateApplicationPayload.result,
                reason = candidateApplicationPayload.reason
            )
        }

        fun buildCandidateTelemetryData(candidateApplicationPayloadList: List<EFCandidateApplicationPayload>): List<EFCandidateTelemetryData> {
            val candidateTelemetryDataList: MutableList<EFCandidateTelemetryData> = mutableListOf()
            candidateApplicationPayloadList.forEach {
                candidateTelemetryDataList.add(buildCandidateTelemetryData(it))
            }
            return candidateTelemetryDataList.toList()
        }

        fun buildUserSelectionTelemetryData(
            efCandidate: EFCandidate,
            candidateIndex: Int,
            hostFunctionTelemetryData: EFHostFunctionTelemetryData?,
            file: PsiFile
        ): EFUserSelectionTelemetryData {
            var positionInHostFunction = -1
            if (hostFunctionTelemetryData != null) {
                positionInHostFunction = efCandidate.lineStart - hostFunctionTelemetryData.bodyLineStart
            }
            return EFUserSelectionTelemetryData(
                lineStart = efCandidate.lineStart,
                lineEnd = efCandidate.lineEnd,
                functionSize = efCandidate.lineEnd - efCandidate.lineStart + 1,
                positionInHostFunction = positionInHostFunction,
                selectedCandidateIndex = candidateIndex,
                candidateType = efCandidate.type,
                elementsType = EFTelemetryDataUtils.buildElementsTypeTelemetryData(efCandidate, file),
            )
        }

        fun buildElementsTypeTelemetryData(
            efCandidate: EFCandidate,
            file: PsiFile
        ): List<EFPsiElementsTypesTelemetryData> {
            val psiElements = file.elementsInRange(TextRange(efCandidate.offsetStart, efCandidate.offsetEnd))
            val namesList = psiElements.filter { it !is PsiWhiteSpace }.map { it.elementType.toString() }
            val namesQuantityMap = namesList.groupingBy { it }.eachCount()
            val result = namesQuantityMap.entries.map {
                EFPsiElementsTypesTelemetryData(
                    type = it.key,
                    quantity = it.value
                )
            }
            return result
        }
    }
}