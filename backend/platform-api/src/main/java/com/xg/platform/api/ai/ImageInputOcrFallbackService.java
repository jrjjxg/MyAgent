package com.xg.platform.api.ai;

import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.contracts.message.ThreadFileReference;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.MimeTypeUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ImageInputOcrFallbackService {

    private static final Logger logger = Logger.getLogger(ImageInputOcrFallbackService.class.getName());
    private static final String OCR_PROMPT = """
            You are an OCR assistant.
            Extract only the readable text from the attached image.
            Preserve line breaks when they carry meaning.
            Do not describe the image or add explanations.
            If there is no readable text, return exactly NO_TEXT.
            """;

    private final ProviderClientResolver providerClientResolver;
    private final AgentChatOptionsFactory chatOptionsFactory;

    ImageInputOcrFallbackService(ProviderClientResolver providerClientResolver,
                                 AgentChatOptionsFactory chatOptionsFactory) {
        this.providerClientResolver = providerClientResolver;
        this.chatOptionsFactory = chatOptionsFactory;
    }

    AgentExecutionRequest prepareForProvider(String providerId, AgentExecutionRequest request) {
        if (!hasImages(request) || supportsVisionInput(providerId)) {
            return request;
        }
        return withOcrText(providerId, request);
    }

    AgentExecutionRequest forceOcrFallback(String providerId, AgentExecutionRequest request) {
        if (!hasImages(request)) {
            return request;
        }
        return withOcrText(providerId, request);
    }

    boolean shouldRetryWithOcr(RuntimeException exception,
                               String providerId,
                               AgentExecutionRequest originalRequest,
                               AgentExecutionRequest preparedRequest) {
        if (!hasImages(originalRequest) || !supportsVisionInput(providerId)) {
            return false;
        }
        if (!hasImages(preparedRequest)) {
            return false;
        }
        String message = safeMessage(exception).toLowerCase(Locale.ROOT);
        if (message.isBlank()) {
            return false;
        }
        return message.contains("image")
                || message.contains("vision")
                || message.contains("media")
                || message.contains("multimodal")
                || message.contains("mime")
                || message.contains("content type")
                || message.contains("unsupported");
    }

    private AgentExecutionRequest withOcrText(String providerId, AgentExecutionRequest request) {
        List<OcrSection> sections = extractOcrSections(providerCandidates(providerId), request.userId(), request.inputImages());
        String mergedMessage = mergeMessageWithOcr(request.message(), sections);
        logger.info(() -> "image OCR fallback applied"
                + " provider=" + providerId
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " images=" + request.inputImages().size());
        return copyWithMessageAndNoImages(request, mergedMessage);
    }

    private List<OcrSection> extractOcrSections(List<String> providerCandidates,
                                                String userId,
                                                List<ThreadFileReference> inputImages) {
        List<OcrSection> sections = new ArrayList<>();
        for (ThreadFileReference image : inputImages) {
            sections.add(new OcrSection(displayName(image), extractTextFromImage(providerCandidates, userId, image)));
        }
        return List.copyOf(sections);
    }

    private String extractTextFromImage(List<String> providerCandidates,
                                        String userId,
                                        ThreadFileReference image) {
        List<String> failures = new ArrayList<>();
        for (String candidateProvider : providerCandidates) {
            if (!supportsVisionInput(candidateProvider)) {
                continue;
            }
            try {
                ProviderClientResolver.ResolvedProviderClient resolved = providerClientResolver.resolve(userId, candidateProvider);
                UserMessage userMessage = UserMessage.builder()
                        .text(OCR_PROMPT)
                        .media(List.of(toMedia(image)))
                        .build();
                ChatResponse response = resolved.chatModel().call(new Prompt(
                        List.of(userMessage),
                        chatOptionsFactory.build(resolved.providerId(), resolved.model(), null, false)
                ));
                String text = response == null || response.getResult() == null || response.getResult().getOutput() == null
                        ? ""
                        : response.getResult().getOutput().getText();
                String normalized = normalizeOcrText(text);
                logger.info(() -> "image OCR completed"
                        + " provider=" + resolved.providerId()
                        + " model=" + resolved.model()
                        + " image=" + displayName(image)
                        + " chars=" + normalized.length());
                return normalized;
            } catch (RuntimeException exception) {
                String failure = candidateProvider + ": " + safeMessage(exception);
                failures.add(failure);
                logger.log(Level.WARNING, "image OCR attempt failed"
                        + " provider=" + candidateProvider
                        + " image=" + displayName(image), exception);
            }
        }
        throw new IllegalStateException("No OCR-capable vision provider could process image " + displayName(image)
                + (failures.isEmpty() ? "" : " -> " + String.join(" | ", failures)));
    }

    private AgentExecutionRequest copyWithMessageAndNoImages(AgentExecutionRequest request, String message) {
        return new AgentExecutionRequest(
                request.userId(),
                request.threadId(),
                request.runId(),
                message,
                request.agentId(),
                request.providerId(),
                request.requestedCapabilities(),
                request.skillIds(),
                request.skillSelectionMode(),
                request.artifacts(),
                request.uploadedFiles(),
                List.of(),
                request.recentMessages(),
                request.sessionSummary(),
                request.longTermMemory(),
                request.chatRouteKind(),
                request.skillRuntimeSnapshot(),
                request.toolUseLimits(),
                request.activeSkillIds(),
                request.selectedDocumentIds()
        );
    }

    private String mergeMessageWithOcr(String originalMessage, List<OcrSection> sections) {
        String trimmed = originalMessage == null ? "" : originalMessage.trim();
        StringBuilder builder = new StringBuilder();
        if (!trimmed.isBlank()) {
            builder.append(trimmed).append("\n\n");
        }
        builder.append("OCR text from attached images:");
        for (OcrSection section : sections) {
            builder.append("\n\n")
                    .append("Image: ")
                    .append(section.name())
                    .append("\n")
                    .append(section.text().isBlank() ? "<no readable text detected>" : section.text());
        }
        return builder.toString();
    }

    private Media toMedia(ThreadFileReference image) {
        Path imagePath = Path.of(image.absolutePath());
        if (!Files.exists(imagePath)) {
            throw new IllegalArgumentException("Image file not found: " + image.absolutePath());
        }
        return Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(image.contentType()))
                .data(new FileSystemResource(imagePath))
                .name(displayName(image))
                .build();
    }

    private List<String> providerCandidates(String selectedProviderId) {
        Set<String> ordered = new LinkedHashSet<>();
        if (selectedProviderId != null && !selectedProviderId.isBlank()) {
            ordered.add(selectedProviderId.trim().toLowerCase(Locale.ROOT));
        }
        ordered.add("openai");
        ordered.add("gemini");
        return List.copyOf(ordered);
    }

    private boolean hasImages(AgentExecutionRequest request) {
        return request != null && request.inputImages() != null && !request.inputImages().isEmpty();
    }

    private boolean supportsVisionInput(String providerId) {
        return "gemini".equalsIgnoreCase(providerId) || "openai".equalsIgnoreCase(providerId);
    }

    private String normalizeOcrText(String text) {
        String normalized = text == null ? "" : text.trim();
        return "NO_TEXT".equalsIgnoreCase(normalized) ? "" : normalized;
    }

    private String displayName(ThreadFileReference image) {
        return image == null || image.name() == null || image.name().isBlank() ? "image" : image.name();
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage().trim();
    }

    private record OcrSection(String name, String text) {
    }
}
