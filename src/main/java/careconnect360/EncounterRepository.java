package careconnect360;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {
    List<Encounter> findAllByOrderByVisitDateDesc();

    List<Encounter> findByPatient_IdOrderByVisitDateDesc(Long patientId);
}