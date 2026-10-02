package careconnect360;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationOrderRepository extends JpaRepository<MedicationOrder, Long> {
    List<MedicationOrder> findAllByOrderByCreatedAtDesc();

    List<MedicationOrder> findByPatient_IdOrderByCreatedAtDesc(Long patientId);
}