package careconnect360;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import careconnect360.AssistantDtos.ChatRequest;
import careconnect360.AssistantDtos.ChatResponse;
import careconnect360.AssistantDtos.ConfirmRequest;
import careconnect360.AssistantDtos.ConfirmResponse;

@RestController
@RequestMapping("/api/assistant")
@CrossOrigin(origins = { "http://localhost:4200", "http://127.0.0.1:4200" })
public class AssistantController {

    private final ClinicalAssistantService assistantService;

    public AssistantController(ClinicalAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return assistantService.chat(request);
    }

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmResponse> confirm(@RequestBody ConfirmRequest request) {
        return ResponseEntity.ok(assistantService.confirm(request));
    }
}