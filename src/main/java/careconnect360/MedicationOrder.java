package careconnect360;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "medication_orders")
public class MedicationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @Column(nullable = false, length = 120)
    private String medicationName;

    @Column(nullable = false, length = 80)
    private String dose;

    @Column(nullable = false, length = 40)
    private String route;

    @Column(nullable = false, length = 120)
    private String frequency;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(length = 1000)
    private String instructions;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public MedicationOrder() {
    }

    @PrePersist
    void prepareOrder() {
        if (status == null || status.isBlank())
            status = "ACTIVE";
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Encounter getEncounter() {
        return encounter;
    }

    public void setEncounter(Encounter encounter) {
        this.encounter = encounter;
    }

    public String getMedicationName() {
        return medicationName;
    }

    public void setMedicationName(String medicationName) {
        this.medicationName = medicationName;
    }

    public String getDose() {
        return dose;
    }

    public void setDose(String dose) {
        this.dose = dose;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}