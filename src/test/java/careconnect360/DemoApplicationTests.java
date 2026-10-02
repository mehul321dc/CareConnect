package careconnect360;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "careconnect.assistant.api-key=")
class DemoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void patientEncounterAndMedicationWorkflow() throws Exception {
		MvcResult patientResult = mockMvc.perform(post("/api/patients")     
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"fullName":"Alex Demo","dateOfBirth":"1990-03-12","sex":"Prefer not to say","phone":"555-0100","email":"alex@example.test","allergies":"No known allergies"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.medicalRecordNumber").isNotEmpty())
				.andReturn();
		JsonNode patient = objectMapper.readTree(patientResult.getResponse().getContentAsString());
		long patientId = patient.get("id").asLong();

		MvcResult encounterResult = mockMvc.perform(post("/api/encounters")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"patientId":%d,"providerName":"Dr. Example","visitType":"Office visit","chiefComplaint":"Routine follow-up","diagnosis":"Stable","clinicalNotes":"Reviewed current symptoms and plan."}
								"""
								.formatted(patientId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.patientId").value(patientId))
				.andReturn();
		long encounterId = objectMapper.readTree(encounterResult.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(post("/api/medications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"patientId":%d,"encounterId":%d,"medicationName":"Example medication","dose":"10 mg","route":"Oral","frequency":"Once daily","startDate":"2026-10-01","instructions":"Take with water."}
								"""
								.formatted(patientId, encounterId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		mockMvc.perform(get("/api/encounters").param("patientId", String.valueOf(patientId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].clinicalNotes").value("Reviewed current symptoms and plan."));

		MvcResult medicationResult = mockMvc
				.perform(get("/api/medications").param("patientId", String.valueOf(patientId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].medicationName").value("Example medication"))
				.andReturn();
		long medicationId = objectMapper.readTree(medicationResult.getResponse().getContentAsString()).get(0).get("id")
				.asLong();

		mockMvc.perform(patch("/api/medications/{id}/status", medicationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"DISCONTINUED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DISCONTINUED"));
	}

	@Test
	void rejectsPatientWithFutureBirthDate() throws Exception {
		mockMvc.perform(post("/api/patients")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName":"Future Patient","dateOfBirth":"2099-01-01","sex":"Other"}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void assistantRequiresServerConfiguredApiKey() throws Exception {
		mockMvc.perform(post("/api/assistant/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"patientId\":null,\"messages\":[{\"role\":\"user\",\"content\":\"Find a patient\"}]}"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void assistantWriteRequiresExplicitConfirmationRequest() throws Exception {
		mockMvc.perform(post("/api/assistant/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"toolName":"create_patient","arguments":{"full_name":"Confirmed Demo","date_of_birth":"1991-04-18","sex":"Prefer not to say","phone":"","email":"","allergies":"No known allergies"},"summary":"Create patient Confirmed Demo"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.containsString("Confirmed Demo")));
	}

}
