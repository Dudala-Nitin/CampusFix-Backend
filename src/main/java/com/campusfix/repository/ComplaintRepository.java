package com.campusfix.repository;

import com.campusfix.model.Category;
import com.campusfix.model.Complaint;
import com.campusfix.model.ComplaintStatus;
import com.campusfix.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByStudentOrderByCreatedAtDesc(User student);

    List<Complaint> findByAssignedStaffOrderByCreatedAtDesc(User staff);

    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    // Used for duplicate/recurring detection: any other complaint with the
    // same category and location that hasn't reached a terminal state yet.
    @Query("SELECT c FROM Complaint c WHERE c.category = :category AND c.location = :location " +
           "AND c.status NOT IN (com.campusfix.model.ComplaintStatus.VERIFIED, com.campusfix.model.ComplaintStatus.REJECTED, com.campusfix.model.ComplaintStatus.CANCELLED) " +
           "ORDER BY c.createdAt ASC")
    List<Complaint> findOpenByCategoryAndLocation(@Param("category") Category category,
                                                   @Param("location") String location);

    long countByStatus(ComplaintStatus status);
    long countByAssignedStaffIsNull();
    List<Complaint> findByAssignedStaffIsNullOrderByCreatedAtDesc();
}
