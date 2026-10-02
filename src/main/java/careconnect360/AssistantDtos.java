package careconnect360;

import java.util.List;

import tools.jackson.databind.JsonNode;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    public record ChatMessage(String role, String content) {
    }

    public record ChatRequest(Long patientId, List<ChatMessage> messages) {
    }

    public record PendingAction(String toolName, JsonNode arguments, String summary) {
    }

    public record ChatResponse(String reply, PendingAction pendingAction) {
    }

    public record ConfirmRequest(String toolName, JsonNode arguments) {
    }

    public record ConfirmResponse(String result) {
    }
}