package careconnect360;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ClinicalDtos {

    private ClinicalDtos() {
    }

    public record EncounterRequest(
            Long patientId,
            String providerName,
            String visitType,
            String chiefComplaint,
            String diagnosis,
            String clinicalNotes,
            LocalDateTime visitDate) {
    }

    public record EncounterView(
            Long id,
            Long patientId,
            String patientName,
            LocalDateTime visitDate,
            String providerName,
            String visitType,
            String chiefComplaint,
            String diagnosis,
            String clinicalNotes,
            String status) {

        static EncounterView from(Encounter encounter) {
            return new EncounterView(
                    encounter.getId(), encounter.getPatient().getId(), encounter.getPatient().getFullName(),
                    encounter.getVisitDate(), encounter.getProviderName(), encounter.getVisitType(),
                    encounter.getChiefComplaint(), encounter.getDiagnosis(), encounter.getClinicalNotes(),
                    encounter.getStatus());
        }
    }

    public record MedicationRequest(
            Long patientId,
            Long encounterId,
            String medicationName,
            String dose,
            String route,
            String frequency,
            LocalDate startDate,
            LocalDate endDate,
            String instructions) {
    }

    public record MedicationStatusRequest(String status) {
    }

    public record MedicationView(
            Long id,
            Long patientId,
            Long encounterId,
            String patientName,
            String medicationName,
            String dose,
            String route,
            String frequency,
            LocalDate startDate,
            LocalDate endDate,
            String instructions,
            String status,
            LocalDateTime createdAt) {

        static MedicationView from(MedicationOrder order) {
            return new MedicationView(
                    order.getId(), order.getPatient().getId(),
                    order.getEncounter() == null ? null : order.getEncounter().getId(),
                    order.getPatient().getFullName(), order.getMedicationName(), order.getDose(),
                    order.getRoute(), order.getFrequency(), order.getStartDate(), order.getEndDate(),
                    order.getInstructions(), order.getStatus(), order.getCreatedAt());
        }
    }
}