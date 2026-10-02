package careconnect360;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import careconnect360.AssistantDtos.ChatMessage;
import careconnect360.AssistantDtos.ChatRequest;
import careconnect360.AssistantDtos.ChatResponse;
import careconnect360.AssistantDtos.ConfirmRequest;
import careconnect360.AssistantDtos.ConfirmResponse;
import careconnect360.AssistantDtos.PendingAction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ClinicalAssistantService {

    private static final int MAX_MESSAGES = 24;
    private static final int MAX_TOOL_ROUNDS = 5;

    private final PatientRepository patientRepository;
    private final EncounterRepository encounterRepository;
    private final MedicationOrderRepository medicationRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public ClinicalAssistantService(
            PatientRepository patientRepository,
            EncounterRepository encounterRepository,
            MedicationOrderRepository medicationRepository,
            ObjectMapper objectMapper,
            @Value("${careconnect.assistant.api-key:}") String apiKey,
            @Value("${careconnect.assistant.model:llama-3.3-70b-versatile}") String model) {
        this.patientRepository = patientRepository;
        this.encounterRepository = encounterRepository;
        this.medicationRepository = medicationRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public ChatResponse chat(ChatRequest request) {
        requireConfigured();
        if (request == null || request.messages() == null || request.messages().isEmpty()
                || request.messages().size() > MAX_MESSAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send between 1 and 24 chat messages.");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(request.patientId())));
        for (ChatMessage message : request.messages()) {
            if (message == null || message.content() == null || message.content().length() > 4000
                    || (!"user".equals(message.role()) && !"assistant".equals(message.role()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat messages are invalid or too long.");
            }
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode assistantMessage = requestModel(messages);
            JsonNode toolCalls = assistantMessage.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                String reply = assistantMessage.path("content").asString("");
                return new ChatResponse(reply.isBlank() ? "I couldn't form a response. Please try again." : reply,
                        null);
            }

            for (JsonNode toolCall : toolCalls) {
                String toolName = toolCall.path("function").path("name").asString("");
                JsonNode arguments = parseArguments(toolCall.path("function").path("arguments").asString("{}"));
                if (isWriteTool(toolName)) {
                    return new ChatResponse("Please review the proposed change and confirm before it is saved.",
                            new PendingAction(toolName, arguments, describeAction(toolName, arguments)));
                }

                messages.add(Map.of(
                        "role", "assistant",
                        "tool_calls", List.of(Map.of(
                                "id", toolCall.path("id").asString(),
                                "type", "function",
                                "function", Map.of("name", toolName, "arguments",
                                        toolCall.path("function").path("arguments").asString("{}"))))));
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCall.path("id").asString(),
                        "content", readTool(toolName, arguments, request.patientId())));
            }
        }
        return new ChatResponse(
                "I found some information, but the request needs to be narrowed down. Try asking about one patient or one record at a time.",
                null);
    }

    @Transactional
    public ConfirmResponse confirm(ConfirmRequest request) {
        if (request == null || request.arguments() == null || !isWriteTool(request.toolName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That proposed action cannot be confirmed.");
        }
        JsonNode args = request.arguments();
        return switch (request.toolName()) {
            case "create_patient" -> createPatient(args);
            case "update_patient" -> updatePatient(args);
            case "create_encounter" -> createEncounter(args);
            case "create_medication" -> createMedication(args);
            case "update_medication_status" -> updateMedicationStatus(args);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported action.");
        };
    }

    private JsonNode requestModel(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", messages,
                    "tools", toolDefinitions(),
                    "tool_choice", "auto",
                    "parallel_tool_calls", false);
            HttpRequest httpRequest = HttpRequest
                    .newBuilder(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(35))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The assistant API key was rejected. Check GROQ_API_KEY.");
            }
            if (response.statusCode() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "The assistant is temporarily rate limited. Try again shortly.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The assistant provider returned an error. Check model configuration and try again.");
            }
            JsonNode responseBody = objectMapper.readTree(response.body());
            JsonNode message = responseBody.path("choices").path(0).path("message");
            if (message.isMissingNode())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The assistant provider returned an empty response.");
            return message;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The assistant request was interrupted.");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the assistant provider.");
        }
    }

    private List<Map<String, Object>> toolDefinitions() {
        return List.of(
                tool("search_patients",
                        "Find patients by name or medical record number. Use the selected patient ID when available.",
                        objectSchema(Map.of(
                                "query",
                                stringProperty("Name or medical record number, or empty for a short patient list.")),
                                List.of("query"))),
                tool("read_patient_chart", "Read the demographics and allergy field for one patient.",
                        objectSchema(Map.of(
                                "patient_id", numberProperty("Patient database ID.")), List.of("patient_id"))),
                tool("read_encounters",
                        "Read visit dates, complaints, diagnoses, and notes for a patient. This returns clinical information; do not infer diagnoses.",
                        objectSchema(Map.of(
                                "patient_id", numberProperty("Patient database ID.")), List.of("patient_id"))),
                tool("read_medications", "Read medication orders and their current status for a patient.",
                        objectSchema(Map.of(
                                "patient_id", numberProperty("Patient database ID.")), List.of("patient_id"))),
                tool("create_patient",
                        "Propose registering a patient. Always ask for explicit confirmation before this is saved.",
                        objectSchema(Map.of(
                                "full_name", stringProperty("Patient full name."),
                                "date_of_birth", stringProperty("Date of birth in YYYY-MM-DD format."),
                                "sex", stringProperty("Patient-provided sex value."),
                                "phone", stringProperty("Optional phone number."),
                                "email", stringProperty("Optional email."),
                                "allergies", stringProperty("Patient-reported allergy information; do not invent.")),
                                List.of("full_name", "date_of_birth", "sex"))),
                tool("update_patient",
                        "Propose updating demographics or allergy information for a patient. Always ask for confirmation before save.",
                        objectSchema(Map.of(
                                "patient_id", numberProperty("Patient database ID."),
                                "full_name", stringProperty("Full name."),
                                "date_of_birth", stringProperty("Date of birth in YYYY-MM-DD format."),
                                "sex", stringProperty("Sex value."),
                                "phone", stringProperty("Phone; use empty string to clear."),
                                "email", stringProperty("Email; use empty string to clear."),
                                "allergies", stringProperty("Allergies; use empty string to clear.")),
                                List.of("patient_id", "full_name", "date_of_birth", "sex"))),
                tool("create_encounter",
                        "Propose saving visit documentation from the user's supplied facts only. Do not generate clinical findings, diagnosis, or treatment plans. Always require confirmation.",
                        objectSchema(Map.of(
                                "patient_id", numberProperty("Patient database ID."),
                                "provider_name", stringProperty("Provider name."),
                                "visit_type", stringProperty("Visit type."),
                                "chief_complaint", stringProperty("Complaint as supplied by user."),
                                "diagnosis", stringProperty("Assessment as supplied by user, or empty."),
                                "clinical_notes", stringProperty("Visit notes as supplied by user.")),
                                List.of("patient_id", "provider_name", "visit_type", "chief_complaint",
                                        "clinical_notes"))),
                tool("create_medication",
                        "Propose a medication order only when the user supplies every sig detail. Never recommend a medication or invent dose, route, frequency, or duration. Always require confirmation.",
                        objectSchema(Map.of(
                                "patient_id", numberProperty("Patient database ID."),
                                "encounter_id", numberProperty("Related encounter ID, or null."),
                                "medication_name", stringProperty("Medication name supplied by the user."),
                                "dose", stringProperty("Dose supplied by the user."),
                                "route", stringProperty("Route supplied by the user."),
                                "frequency", stringProperty("Frequency supplied by the user."),
                                "start_date", stringProperty("Start date YYYY-MM-DD."),
                                "end_date", stringProperty("End date YYYY-MM-DD, or empty."),
                                "instructions", stringProperty("Optional instructions supplied by the user.")),
                                List.of("patient_id", "medication_name", "dose", "route", "frequency", "start_date"))),
                tool("update_medication_status",
                        "Propose marking an existing medication order active or discontinued. Always require confirmation.",
                        objectSchema(Map.of(
                                "medication_id", numberProperty("Medication order database ID."),
                                "status", stringProperty("ACTIVE or DISCONTINUED.")),
                                List.of("medication_id", "status"))));
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "function", Map.of(
                "name", name, "description", description, "parameters", parameters, "strict", false));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }

    private Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> numberProperty(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private String systemPrompt(Long selectedPatientId) {
        String selected = selectedPatientId == null ? "No patient is selected."
                : "The UI's selected patient ID is " + selectedPatientId + ".";
        return "You are CareConnect's EHR workflow assistant. " + selected + " "
                + "Help staff navigate and summarize only information returned by the record tools. "
                + "You are not a clinician: do not diagnose, recommend treatment, recommend drugs, interpret symptoms, or invent chart facts. "
                + "When asked to save or change records, collect missing factual fields, call the appropriate write tool, and tell the user the exact proposed action awaits confirmation. "
                + "Never claim a write completed before its confirmation result is returned. Keep replies concise and identify when information is not in the chart. "
                + "This demo has no authentication and is not for real patient data.";
    }

    private String readTool(String name, JsonNode args, Long selectedPatientId) {
        return switch (name) {
            case "search_patients" -> searchPatients(args.path("query").asString(""));
            case "read_patient_chart" -> readPatient(args.path("patient_id").asLong(-1));
            case "read_encounters" -> readEncounters(args.path("patient_id").asLong(-1));
            case "read_medications" -> readMedications(args.path("patient_id").asLong(-1));
            default -> "Unsupported read operation.";
        };
    }

    private String searchPatients(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matches = patientRepository.findAllByOrderByFullNameAsc().stream()
                .filter(patient -> normalized.isBlank()
                        || patient.getFullName().toLowerCase(Locale.ROOT).contains(normalized)
                        || patient.getMedicalRecordNumber().toLowerCase(Locale.ROOT).contains(normalized))
                .limit(15)
                .map(patient -> Map.<String, Object>of(
                        "id", patient.getId(), "name", patient.getFullName(), "mrn", patient.getMedicalRecordNumber(),
                        "dateOfBirth", patient.getDateOfBirth().toString()))
                .toList();
        return json(matches);
    }

    private String readPatient(long id) {
        return patientRepository.findById(id).map(patient -> json(Map.of(
                "id", patient.getId(), "name", patient.getFullName(), "mrn", patient.getMedicalRecordNumber(),
                "dateOfBirth", patient.getDateOfBirth().toString(), "sex", patient.getSex(),
                "phone", valueOrEmpty(patient.getPhone()), "email", valueOrEmpty(patient.getEmail()),
                "allergies", valueOrEmpty(patient.getAllergies()))))
                .orElse("Patient not found.");
    }

    private String readEncounters(long id) {
        if (!patientRepository.existsById(id))
            return "Patient not found.";
        return json(encounterRepository.findByPatient_IdOrderByVisitDateDesc(id).stream().map(encounter -> Map.of(
                "id", encounter.getId(), "date", encounter.getVisitDate().toString(), "provider",
                encounter.getProviderName(),
                "type", encounter.getVisitType(), "complaint", encounter.getChiefComplaint(),
                "diagnosis", valueOrEmpty(encounter.getDiagnosis()), "notes", encounter.getClinicalNotes())).toList());
    }

    private String readMedications(long id) {
        if (!patientRepository.existsById(id))
            return "Patient not found.";
        return json(medicationRepository.findByPatient_IdOrderByCreatedAtDesc(id).stream().map(order -> Map.of(
                "id", order.getId(), "medication", order.getMedicationName(), "dose", order.getDose(),
                "route", order.getRoute(), "frequency", order.getFrequency(), "startDate",
                order.getStartDate().toString(),
                "endDate", order.getEndDate() == null ? "" : order.getEndDate().toString(), "status", order.getStatus(),
                "instructions", valueOrEmpty(order.getInstructions()))).toList());
    }

    private ConfirmResponse createPatient(JsonNode args) {
        Patient patient = new Patient();
        patient.setFullName(text(args, "full_name"));
        patient.setDateOfBirth(date(args, "date_of_birth"));
        patient.setSex(text(args, "sex"));
        patient.setPhone(optionalText(args, "phone"));
        patient.setEmail(optionalText(args, "email"));
        patient.setAllergies(optionalText(args, "allergies"));
        validatePatient(patient);
        patient.setMedicalRecordNumber(null);
        patient.setCreatedAt(null);
        Patient saved = patientRepository.save(patient);
        return new ConfirmResponse(
                "Created patient " + saved.getFullName() + " (" + saved.getMedicalRecordNumber() + ").");
    }

    private ConfirmResponse updatePatient(JsonNode args) {
        long id = requiredId(args, "patient_id");
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found."));
        patient.setFullName(text(args, "full_name"));
        patient.setDateOfBirth(date(args, "date_of_birth"));
        patient.setSex(text(args, "sex"));
        patient.setPhone(optionalText(args, "phone"));
        patient.setEmail(optionalText(args, "email"));
        patient.setAllergies(optionalText(args, "allergies"));
        validatePatient(patient);
        patientRepository.save(patient);
        return new ConfirmResponse(
                "Updated patient " + patient.getFullName() + " (" + patient.getMedicalRecordNumber() + ").");
    }

    private ConfirmResponse createEncounter(JsonNode args) {
        long patientId = requiredId(args, "patient_id");
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found."));
        String provider = text(args, "provider_name");
        String type = text(args, "visit_type");
        String complaint = text(args, "chief_complaint");
        String notes = text(args, "clinical_notes");
        String diagnosis = optionalText(args, "diagnosis");
        if (provider.length() > 120 || type.length() > 48 || complaint.length() > 500
                || (diagnosis != null && diagnosis.length() > 500) || notes.length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A visit field is too long.");
        }
        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        encounter.setProviderName(provider);
        encounter.setVisitType(type);
        encounter.setChiefComplaint(complaint);
        encounter.setDiagnosis(diagnosis);
        encounter.setClinicalNotes(notes);
        encounterRepository.save(encounter);
        return new ConfirmResponse("Saved visit note for " + patient.getFullName() + ".");
    }

    private ConfirmResponse createMedication(JsonNode args) {
        long patientId = requiredId(args, "patient_id");
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found."));
        Encounter encounter = null;
        if (args.hasNonNull("encounter_id")) {
            encounter = encounterRepository.findById(args.get("encounter_id").asLong())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encounter not found."));
            if (!encounter.getPatient().getId().equals(patientId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The visit belongs to another patient.");
            }
        }
        MedicationOrder order = new MedicationOrder();
        order.setPatient(patient);
        order.setEncounter(encounter);
        order.setMedicationName(text(args, "medication_name"));
        order.setDose(text(args, "dose"));
        order.setRoute(text(args, "route"));
        order.setFrequency(text(args, "frequency"));
        order.setStartDate(date(args, "start_date"));
        order.setEndDate(optionalDate(args, "end_date"));
        order.setInstructions(optionalText(args, "instructions"));
        order.setStatus("ACTIVE");
        validateMedication(order);
        medicationRepository.save(order);
        return new ConfirmResponse(
                "Saved medication order " + order.getMedicationName() + " for " + patient.getFullName() + ".");
    }

    private ConfirmResponse updateMedicationStatus(JsonNode args) {
        long id = requiredId(args, "medication_id");
        String status = text(args, "status").toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(status) && !"DISCONTINUED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or DISCONTINUED.");
        }
        MedicationOrder order = medicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication order not found."));
        order.setStatus(status);
        medicationRepository.save(order);
        return new ConfirmResponse("Marked " + order.getMedicationName() + " " + status.toLowerCase(Locale.ROOT) + ".");
    }

    private void validatePatient(Patient patient) {
        if (patient.getFullName() == null || patient.getFullName().isBlank() || patient.getFullName().length() > 120
                || patient.getDateOfBirth() == null || patient.getDateOfBirth().isAfter(LocalDate.now())
                || patient.getSex() == null || patient.getSex().isBlank() || patient.getSex().length() > 32
                || (patient.getPhone() != null && patient.getPhone().length() > 40)
                || (patient.getEmail() != null && patient.getEmail().length() > 160)
                || (patient.getAllergies() != null && patient.getAllergies().length() > 2000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient details are invalid.");
        }
    }

    private void validateMedication(MedicationOrder order) {
        if (order.getMedicationName().isBlank() || order.getMedicationName().length() > 120
                || order.getDose().isBlank() || order.getDose().length() > 80
                || order.getRoute().isBlank() || order.getRoute().length() > 40
                || order.getFrequency().isBlank() || order.getFrequency().length() > 120
                || order.getStartDate() == null
                || (order.getEndDate() != null && order.getEndDate().isBefore(order.getStartDate()))
                || (order.getInstructions() != null && order.getInstructions().length() > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Medication order details are invalid.");
        }
    }

    private boolean isWriteTool(String name) {
        return List.of("create_patient", "update_patient", "create_encounter", "create_medication",
                "update_medication_status").contains(name);
    }

    private String describeAction(String name, JsonNode args) {
        String patient = args.path("full_name").asString("patient #" + args.path("patient_id").asString(""));
        return switch (name) {
            case "create_patient" -> "Create patient record for " + patient;
            case "update_patient" -> "Update demographics for " + patient;
            case "create_encounter" -> "Save visit note for patient #" + args.path("patient_id").asString("") + ": "
                    + args.path("chief_complaint").asString("");
            case "create_medication" ->
                "Order " + args.path("medication_name").asString("") + " " + args.path("dose").asString("")
                        + " for patient #" + args.path("patient_id").asString("") + " ("
                        + args.path("frequency").asString("") + ")";
            case "update_medication_status" -> "Mark medication order #" + args.path("medication_id").asString("")
                    + " " + args.path("status").asString("");
            default -> "Confirm this EHR change";
        };
    }

    private JsonNode parseArguments(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The assistant proposed malformed tool arguments.");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encode chart data.");
        }
    }

    private String text(JsonNode args, String field) {
        String value = args.path(field).asString("").trim();
        if (value.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required field: " + field);
        return value;
    }

    private String optionalText(JsonNode args, String field) {
        String value = args.path(field).asString("").trim();
        return value.isBlank() ? null : value;
    }

    private long requiredId(JsonNode args, String field) {
        long id = args.path(field).asLong(-1);
        if (id <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing or invalid field: " + field);
        return id;
    }

    private LocalDate date(JsonNode args, String field) {
        try {
            return LocalDate.parse(text(args, field));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must use YYYY-MM-DD.");
        }
    }

    private LocalDate optionalDate(JsonNode args, String field) {
        String value = optionalText(args, field);
        if (value == null)
            return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must use YYYY-MM-DD.");
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void requireConfigured() {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chat assistant is not configured. Set GROQ_API_KEY in the backend environment.");
        }
    }
}