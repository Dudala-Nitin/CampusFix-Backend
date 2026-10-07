package com.campusfix.service;

import com.campusfix.model.Complaint;
import com.campusfix.model.ComplaintUpdate;
import com.campusfix.model.User;
import com.campusfix.repository.ComplaintUpdateRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ComplaintUpdateService {
    private final ComplaintUpdateRepository repository;
    private final ComplaintService complaints;

    public ComplaintUpdateService(ComplaintUpdateRepository repository, ComplaintService complaints) {
        this.repository = repository;
        this.complaints = complaints;
    }

    public List<ComplaintUpdate> findForComplaint(Long complaintId, User staff) {
        Complaint complaint = complaints.getByIdOrThrow(complaintId);
        if (complaint.getAssignedStaff() == null || !complaint.getAssignedStaff().getId().equals(staff.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return repository.findByComplaintOrderByCreatedAtDesc(complaint);
    }

    public ComplaintUpdate create(Long complaintId, User staff, String description, boolean draft) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Progress description is required.");
        Complaint complaint = complaints.getByIdOrThrow(complaintId);
        if (complaint.getAssignedStaff() == null || !complaint.getAssignedStaff().getId().equals(staff.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        ComplaintUpdate update = new ComplaintUpdate();
        update.setComplaint(complaint);
        update.setStaff(staff);
        update.setDescription(description);
        update.setDraft(draft);
        return repository.save(update);
    }

    public ComplaintUpdate update(Long id, User staff, String description) {
        ComplaintUpdate update = repository.findByIdAndStaff(id, staff)
                .orElseThrow(() -> new IllegalArgumentException("Progress update not found."));
        if (!update.isDraft()) throw new IllegalStateException("Submitted progress updates cannot be edited.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Progress description is required.");
        update.setDescription(description);
        return repository.save(update);
    }

    public void delete(Long id, User staff) {
        ComplaintUpdate update = repository.findByIdAndStaff(id, staff)
                .orElseThrow(() -> new IllegalArgumentException("Progress update not found."));
        if (!update.isDraft()) throw new IllegalStateException("Submitted progress updates cannot be deleted.");
        repository.delete(update);
    }
}
