package careconnect360;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
@CrossOrigin(origins = { "http://localhost:4200", "http://127.0.0.1:4200" })
public class PatientController {

    private final PatientRepository patientRepository;

    public PatientController(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @GetMapping
    public List<Patient> getPatients() {
        return patientRepository.findAllByOrderByFullNameAsc();
    }

    @PostMapping
    public ResponseEntity<Patient> createPatient(@RequestBody Patient patient) {
        if (!isValid(patient))
            return ResponseEntity.badRequest().build();

        patient.setId(null);
        patient.setMedicalRecordNumber(null);
        patient.setCreatedAt(null);
        normalize(patient);
        return ResponseEntity.status(201).body(patientRepository.save(patient));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Patient> updatePatient(@PathVariable Long id, @RequestBody Patient request) {
        if (!isValid(request))
            return ResponseEntity.badRequest().build();

        return patientRepository.findById(id).map(patient -> {
            patient.setFullName(request.getFullName().trim());
            patient.setDateOfBirth(request.getDateOfBirth());
            patient.setSex(request.getSex().trim());
            patient.setPhone(clean(request.getPhone()));
            patient.setEmail(clean(request.getEmail()));
            patient.setAllergies(clean(request.getAllergies()));
            return ResponseEntity.ok(patientRepository.save(patient));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean isValid(Patient patient) {
        return patient != null
                && hasText(patient.getFullName()) && patient.getFullName().trim().length() <= 120
                && patient.getDateOfBirth() != null && !patient.getDateOfBirth().isAfter(LocalDate.now())
                && hasText(patient.getSex()) && patient.getSex().trim().length() <= 32
                && lengthAtMost(patient.getPhone(), 40)
                && lengthAtMost(patient.getEmail(), 160)
                && lengthAtMost(patient.getAllergies(), 2000);
    }

    private void normalize(Patient patient) {
        patient.setFullName(patient.getFullName().trim());
        patient.setSex(patient.getSex().trim());
        patient.setPhone(clean(patient.getPhone()));
        patient.setEmail(clean(patient.getEmail()));
        patient.setAllergies(clean(patient.getAllergies()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean lengthAtMost(String value, int max) {
        return value == null || value.length() <= max;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}