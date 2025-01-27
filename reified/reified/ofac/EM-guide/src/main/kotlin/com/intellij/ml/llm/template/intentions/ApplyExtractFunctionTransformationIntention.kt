package com.intellij.ml.llm.template.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.LLMBaseResponse
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.sendChatRequest
import com.intellij.ml.llm.template.prompts.fewShotExtractSuggestion
import com.intellij.ml.llm.template.showEFNotification
import com.intellij.ml.llm.template.telemetry.*
import com.intellij.ml.llm.template.ui.ExtractFunctionPanel
import com.intellij.ml.llm.template.utils.*
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import java.awt.Point
import java.awt.Rectangle
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


@Suppress("UnstableApiUsage")
abstract class ApplyExtractFunctionTransformationIntention(
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
) : IntentionAction {
    private val logger = Logger.getInstance("#com.intellij.ml.llm")
    private val codeTransformer = CodeTransformer()
    private val telemetryDataManager = EFTelemetryDataManager()
    private var llmResponseTime = 0L

    init {
        codeTransformer.addObserver(EFLoggerObserver(logger))
        codeTransformer.addObserver(TelemetryDataObserver())
    }

    override fun getFamilyName(): String = LLMBundle.message("intentions.apply.transformation.family.name")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && file != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val selectionModel = editor.selectionModel
        val namedElement = PsiUtils.getParentFunctionOrNull(editor, file.language)
        if (namedElement != null) {
            telemetryDataManager.newSession()
            val codeSnippet = namedElement.text

            val textRange = namedElement.textRange
            selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
            val startLineNumber = editor.document.getLineNumber(selectionModel.selectionStart) + 1
            val withLineNumbers = addLineNumbersToCodeSnippet(codeSnippet, startLineNumber)

            telemetryDataManager.addHostFunctionTelemetryData(
                EFTelemetryDataUtils.buildHostFunctionTelemetryData(
                    codeSnippet = codeSnippet,
                    lineStart = startLineNumber,
                    bodyLineStart = PsiUtils.getFunctionBodyStartLine(namedElement),
                    language = file.language.id.toLowerCaseAsciiOnly()
                )
            )

            invokeLlm(withLineNumbers, project, editor, file)
        }
    }

    private fun invokeLlm(text: String, project: Project, editor: Editor, file: PsiFile) {
        logger.info("Invoking LLM with text: $text")
        val messageList = fewShotExtractSuggestion(text)

        val task = object : Task.Backgroundable(
            project, LLMBundle.message("intentions.request.extract.function.background.process.title")
        ) {
            override fun run(indicator: ProgressIndicator) {
                val now = System.nanoTime()
                val response = sendChatRequest(
                    project, messageList, efLLMRequestProvider.chatModel, efLLMRequestProvider
                )
                if (response != null) {
                    invokeLater {
                        llmResponseTime = System.nanoTime() - now
                        if (response.getSuggestions().isEmpty()) {
                            showEFNotification(
                                project,
                                LLMBundle.message("notification.extract.function.with.llm.no.suggestions.message"),
                                NotificationType.INFORMATION
                            )
                        } else {
                            processLLMResponse(response, project, editor, file)
                        }
                    }
                }
            }
        }
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
    }

    private fun filterCandidates(
        candidates: List<EFCandidate>,
        candidatesApplicationTelemetryObserver: EFCandidatesApplicationTelemetryObserver,
        editor: Editor,
        file: PsiFile
    ): List<EFCandidate> {
        val filteredCandidates = candidates.filter {
            isCandidateExtractable(
                it, editor, file, listOf(EFLoggerObserver(logger), candidatesApplicationTelemetryObserver)
            )
        }.sortedByDescending { it.lineEnd - it.lineStart }

        return filteredCandidates
    }

    private fun processLLMResponse(response: LLMBaseResponse, project: Project, editor: Editor, file: PsiFile) {
        val now = System.nanoTime()

        val llmResponse = response.getSuggestions()[0]
        val efSuggestionList = identifyExtractFunctionSuggestions(llmResponse.text)
        val candidates = EFCandidateFactory().buildCandidates(efSuggestionList.suggestionList, editor, file).toList()
        if (candidates.isEmpty()) {
            showEFNotification(
                project,
                LLMBundle.message("notification.extract.function.with.llm.no.suggestions.message"),
                NotificationType.INFORMATION
            )
            telemetryDataManager.addCandidatesTelemetryData(buildCandidatesTelemetryData(0, emptyList()))
            buildProcessingTimeTelemetryData(llmResponseTime, System.nanoTime() - now)
            sendTelemetryData()
        } else {
            val candidatesApplicationTelemetryObserver = EFCandidatesApplicationTelemetryObserver()
            val filteredCandidates = filterCandidates(candidates, candidatesApplicationTelemetryObserver, editor, file)

            telemetryDataManager.addCandidatesTelemetryData(
                buildCandidatesTelemetryData(
                    efSuggestionList.suggestionList.size,
                    candidatesApplicationTelemetryObserver.getData()
                )
            )
            buildProcessingTimeTelemetryData(llmResponseTime, System.nanoTime() - now)

            if (filteredCandidates.isEmpty()) {
                showEFNotification(
                    project,
                    LLMBundle.message("notification.extract.function.with.llm.no.extractable.candidates.message"),
                    NotificationType.INFORMATION
                )
                sendTelemetryData()
            } else {
                showExtractFunctionPopup(project, editor, file, filteredCandidates, codeTransformer)
            }
        }
    }

    private fun showExtractFunctionPopup(
        project: Project,
        editor: Editor,
        file: PsiFile,
        candidates: List<EFCandidate>,
        codeTransformer: CodeTransformer
    ) {
        val highlighter = AtomicReference(ScopeHighlighter(editor))
        val efPanel = ExtractFunctionPanel(
            project = project,
            editor = editor,
            file = file,
            candidates = candidates,
            codeTransformer = codeTransformer,
            highlighter = highlighter,
            efTelemetryDataManager = telemetryDataManager
        )
        val elapsedTimeTelemetryDataObserver = TelemetryElapsedTimeObserver()
        efPanel.addObserver(elapsedTimeTelemetryDataObserver)
        val panel = efPanel.createPanel()

        // Create the popup
        val efPopup =
            JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, efPanel.myExtractFunctionsCandidateTable)
                .setRequestFocus(true)
                .setTitle(LLMBundle.message("ef.candidates.popup.title"))
                .setResizable(true)
                .setMovable(true).createPopup()

        // Add onClosed listener
        efPopup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                elapsedTimeTelemetryDataObserver.update(
                    EFNotification(
                        EFTelemetryDataElapsedTimeNotificationPayload(TelemetryDataAction.STOP, 0)
                    )
                )
                buildElapsedTimeTelemetryData(elapsedTimeTelemetryDataObserver)
                highlighter.getAndSet(null).dropHighlight()
                sendTelemetryData()
            }

            override fun beforeShown(event: LightweightWindowEvent) {
                super.beforeShown(event)
                elapsedTimeTelemetryDataObserver.update(
                    EFNotification(
                        EFTelemetryDataElapsedTimeNotificationPayload(TelemetryDataAction.START, 0)
                    )
                )
            }
        })

        // set the popup as delegate to the Extract Function panel
        efPanel.setDelegatePopup(efPopup)

        // Show the popup at the top right corner of the current editor
        val contentComponent = editor.contentComponent
        val visibleRect: Rectangle = contentComponent.visibleRect
        val point = Point(visibleRect.x + visibleRect.width - 500, visibleRect.y)
        efPopup.show(RelativePoint(contentComponent, point))
    }

    abstract fun getInstruction(project: Project, editor: Editor): String?

    override fun startInWriteAction(): Boolean = false

    private fun sendTelemetryData() {
        val efTelemetryData = telemetryDataManager.getData()
        if (efTelemetryData != null) {
            TelemetryDataObserver().update(EFNotification(efTelemetryData))
        }
    }

    private fun buildProcessingTimeTelemetryData(llmResponseTime: Long, pluginProcessingTime: Long) {
        val llmResponseTimeMillis = TimeUnit.NANOSECONDS.toMillis(llmResponseTime)
        val pluginProcessingTimeMillis = TimeUnit.NANOSECONDS.toMillis(pluginProcessingTime)
        val efTelemetryData = telemetryDataManager.getData()
        if (efTelemetryData != null) {
            efTelemetryData.processingTime = EFTelemetryDataProcessingTime(

                llmResponseTime = llmResponseTimeMillis,
                pluginProcessingTime = pluginProcessingTimeMillis,
                totalTime = llmResponseTimeMillis + pluginProcessingTimeMillis
            )
        }
    }

    private fun buildCandidatesTelemetryData(
        numberOfSuggestions: Int, notificationPayloadList: List<EFCandidateApplicationPayload>
    ): EFCandidatesTelemetryData {
        val candidateTelemetryDataList = EFTelemetryDataUtils.buildCandidateTelemetryData(notificationPayloadList)
        return EFCandidatesTelemetryData(
            numberOfSuggestions = numberOfSuggestions, candidates = candidateTelemetryDataList
        )
    }

    private fun buildElapsedTimeTelemetryData(elapsedTimeTelemetryDataObserver: TelemetryElapsedTimeObserver) {
        val elapsedTimeTelemetryData = elapsedTimeTelemetryDataObserver.getTelemetryData()
        val efTelemetryData = telemetryDataManager.getData()
        if (efTelemetryData != null) {
            efTelemetryData.elapsedTime = elapsedTimeTelemetryData
        }
    }
}