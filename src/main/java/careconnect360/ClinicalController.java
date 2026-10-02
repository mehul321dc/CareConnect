package careconnect360;

import java.util.List;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import careconnect360.ClinicalDtos.EncounterRequest;
import careconnect360.ClinicalDtos.EncounterView;
import careconnect360.ClinicalDtos.MedicationRequest;
import careconnect360.ClinicalDtos.MedicationStatusRequest;
import careconnect360.ClinicalDtos.MedicationView;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = { "http://localhost:4200", "http://127.0.0.1:4200" })
public class ClinicalController {

    private final PatientRepository patientRepository;
    private final EncounterRepository encounterRepository;
    private final MedicationOrderRepository medicationRepository;

    public ClinicalController(
            PatientRepository patientRepository,
            EncounterRepository encounterRepository,
            MedicationOrderRepository medicationRepository) {
        this.patientRepository = patientRepository;
        this.encounterRepository = encounterRepository;
        this.medicationRepository = medicationRepository;
    }

    @GetMapping("/encounters")
    @Transactional(readOnly = true)
    public List<EncounterView> getEncounters(@RequestParam(required = false) Long patientId) {
        List<Encounter> encounters = patientId == null
                ? encounterRepository.findAllByOrderByVisitDateDesc()
                : encounterRepository.findByPatient_IdOrderByVisitDateDesc(patientId);
        return encounters.stream().map(EncounterView::from).toList();
    }

    @PostMapping("/encounters")
    @Transactional
    public ResponseEntity<EncounterView> createEncounter(@RequestBody EncounterRequest request) {
        if (request == null || request.patientId() == null || !hasText(request.providerName())
                || !hasText(request.visitType()) || !hasText(request.chiefComplaint())
                || !hasText(request.clinicalNotes()) || tooLong(request.providerName(), 120)
                || tooLong(request.visitType(), 48) || tooLong(request.chiefComplaint(), 500)
                || tooLong(request.diagnosis(), 500) || tooLong(request.clinicalNotes(), 5000)) {
            return ResponseEntity.badRequest().build();
        }

        Patient patient = patientRepository.findById(request.patientId()).orElse(null);
        if (patient == null)
            return ResponseEntity.badRequest().build();

        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        encounter.setProviderName(request.providerName().trim());
        encounter.setVisitType(request.visitType().trim());
        encounter.setChiefComplaint(request.chiefComplaint().trim());
        encounter.setDiagnosis(clean(request.diagnosis()));
        encounter.setClinicalNotes(request.clinicalNotes().trim());
        encounter.setVisitDate(request.visitDate());
        return ResponseEntity.status(201).body(EncounterView.from(encounterRepository.save(encounter)));
    }

    @GetMapping("/medications")
    @Transactional(readOnly = true)
    public List<MedicationView> getMedications(@RequestParam(required = false) Long patientId) {
        List<MedicationOrder> orders = patientId == null
                ? medicationRepository.findAllByOrderByCreatedAtDesc()
                : medicationRepository.findByPatient_IdOrderByCreatedAtDesc(patientId);
        return orders.stream().map(MedicationView::from).toList();
    }

    @PostMapping("/medications")
    @Transactional
    public ResponseEntity<MedicationView> createMedication(@RequestBody MedicationRequest request) {
        if (request == null || request.patientId() == null || !hasText(request.medicationName())
                || !hasText(request.dose()) || !hasText(request.route()) || !hasText(request.frequency())
                || request.startDate() == null || tooLong(request.medicationName(), 120)
                || tooLong(request.dose(), 80) || tooLong(request.route(), 40)
                || tooLong(request.frequency(), 120) || tooLong(request.instructions(), 1000)
                || (request.endDate() != null && request.endDate().isBefore(request.startDate()))) {
            return ResponseEntity.badRequest().build();
        }

        Patient patient = patientRepository.findById(request.patientId()).orElse(null);
        if (patient == null)
            return ResponseEntity.badRequest().build();

        Encounter encounter = null;
        if (request.encounterId() != null) {
            encounter = encounterRepository.findById(request.encounterId()).orElse(null);
            if (encounter == null || !encounter.getPatient().getId().equals(patient.getId())) {
                return ResponseEntity.badRequest().build();
            }
        }

        MedicationOrder order = new MedicationOrder();
        order.setPatient(patient);
        order.setEncounter(encounter);
        order.setMedicationName(request.medicationName().trim());
        order.setDose(request.dose().trim());
        order.setRoute(request.route().trim());
        order.setFrequency(request.frequency().trim());
        order.setStartDate(request.startDate());
        order.setEndDate(request.endDate());
        order.setInstructions(clean(request.instructions()));
        order.setStatus("ACTIVE");
        return ResponseEntity.status(201).body(MedicationView.from(medicationRepository.save(order)));
    }

    @PatchMapping("/medications/{id}/status")
    @Transactional
    public ResponseEntity<MedicationView> updateMedicationStatus(
            @PathVariable Long id, @RequestBody MedicationStatusRequest request) {
        if (request == null || request.status() == null)
            return ResponseEntity.badRequest().build();
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE") && !status.equals("DISCONTINUED")) {
            return ResponseEntity.badRequest().build();
        }

        return medicationRepository.findById(id).map(order -> {
            order.setStatus(status);
            return ResponseEntity.ok(MedicationView.from(medicationRepository.save(order)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean tooLong(String value, int max) {
        return value != null && value.length() > max;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}