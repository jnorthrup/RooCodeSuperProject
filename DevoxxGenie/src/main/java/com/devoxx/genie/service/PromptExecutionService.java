package com.devoxx.genie.service;

import com.devoxx.genie.error.ErrorHandler;
import com.devoxx.genie.model.Constant;
import com.devoxx.genie.model.enumarations.ModelProvider;
import com.devoxx.genie.model.request.ChatMessageContext;
import com.devoxx.genie.service.exception.ModelNotActiveException;
import com.devoxx.genie.service.exception.ProviderUnavailableException;
import com.devoxx.genie.ui.settings.DevoxxGenieStateService;
import com.devoxx.genie.util.ChatMessageContextUtil;
import com.devoxx.genie.util.ClipboardUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class PromptExecutionService {

    private static final Logger LOG = Logger.getInstance(PromptExecutionService.class);
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private CompletableFuture<Response<AiMessage>> queryFuture = null;

    @Getter
    private boolean running = false;

    private final ReentrantLock queryLock = new ReentrantLock();

    @NotNull
    static PromptExecutionService getInstance() {
        return ApplicationManager.getApplication().getService(PromptExecutionService.class);
    }

    /**
     * Execute the query with the given language text pair and chat language model.
     *
     * @param chatMessageContext the chat message context
     * @return the response
     */
    public @NotNull CompletableFuture<Response<AiMessage>> executeQuery(@NotNull ChatMessageContext chatMessageContext) {
        LOG.debug("Execute query : " + chatMessageContext);

        queryLock.lock();
        try {
            if (isCanceled()) return CompletableFuture.completedFuture(null);

            ChatMemoryService chatMemoryService = ChatMemoryService.getInstance();

            // Add System Message if ChatMemoryService is empty
            if (ChatMemoryService.getInstance().isEmpty(chatMessageContext.getProject())) {
                LOG.debug("ChatMemoryService is empty, adding a new SystemMessage");

                if (!ChatMessageContextUtil.isOpenAIo1Model(chatMessageContext.getLanguageModel())) {
                    String systemPrompt = DevoxxGenieStateService.getInstance().getSystemPrompt() + Constant.MARKDOWN;
                    chatMemoryService.add(chatMessageContext.getProject(), SystemMessage.from(systemPrompt));
                }
            }

            // Add User message to context
            MessageCreationService.getInstance().addUserMessageToContext(chatMessageContext);
            chatMemoryService.add(chatMessageContext.getProject(), chatMessageContext.getUserMessage());

            long startTime = System.currentTimeMillis();

            queryFuture = CompletableFuture
                .supplyAsync(() -> processChatMessage(chatMessageContext), queryExecutor)
                .orTimeout(
                    chatMessageContext.getTimeout() == null ? 60 : chatMessageContext.getTimeout(), TimeUnit.SECONDS)
                .thenApply(result -> {
                    chatMessageContext.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                    return result;
                })
                .exceptionally(throwable -> {
                    LOG.error("Error occurred while processing chat message", throwable);
                    ErrorHandler.handleError(chatMessageContext.getProject(), throwable);
                    return null;
                });
        } finally {
            queryLock.unlock();
        }
        return queryFuture;
    }

    /**
     * If the future task is not null this means we need to cancel it
     *
     * @return true if the task is canceled
     */
    private boolean isCanceled() {
        if (queryFuture != null && !queryFuture.isDone()) {
            queryFuture.cancel(true);
            running = false;
            return true;
        }
        running = true;
        return false;
    }

    /**
     * Process the chat message.
     *
     * @param chatMessageContext the chat message context
     * @return the AI response
     */
    private @NotNull Response<AiMessage> processChatMessage(ChatMessageContext chatMessageContext) {
        try {
            ChatLanguageModel chatLanguageModel = chatMessageContext.getChatLanguageModel();
            List<ChatMessage> messages = ChatMemoryService.getInstance().messages(chatMessageContext.getProject());

            ClipboardUtil.copyToClipboard(messages.toString());

            Response<AiMessage> response = chatLanguageModel.generate(messages);
            ChatMemoryService.getInstance().add(chatMessageContext.getProject(), response.content());
            return response;
        } catch (Exception e) {
            if (chatMessageContext.getLanguageModel().getProvider().equals(ModelProvider.Jan)) {
                throw new ModelNotActiveException("Selected Jan model is not active. Download and make it active or add API Key in Jan settings.");
            }
            ChatMemoryService.getInstance().removeLast(chatMessageContext.getProject());
            throw new ProviderUnavailableException(e.getMessage());
        }
    }
}
