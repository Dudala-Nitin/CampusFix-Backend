package com.campusfix.repository;

import com.campusfix.model.Complaint;
import com.campusfix.model.ComplaintUpdate;
import com.campusfix.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ComplaintUpdateRepository extends JpaRepository<ComplaintUpdate, Long> {
    List<ComplaintUpdate> findByComplaintOrderByCreatedAtDesc(Complaint complaint);
    Optional<ComplaintUpdate> findByIdAndStaff(Long id, User staff);
}
