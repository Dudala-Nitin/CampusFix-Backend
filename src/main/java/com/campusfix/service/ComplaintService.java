package com.campusfix.service;

import com.campusfix.model.*;
import com.campusfix.repository.ComplaintRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final NotificationService notificationService;

    public ComplaintService(ComplaintRepository complaintRepository, NotificationService notificationService) {
        this.complaintRepository = complaintRepository;
        this.notificationService = notificationService;
    }

    /**
     * Creates a new complaint. Before saving, checks whether an open complaint
     * already exists for the same category + location: if so, this one is
     * flagged as a duplicate/recurrence of it rather than treated as a fresh,
     * unrelated issue, and its priority is bumped since a repeated report
     * signals the problem is worse or more urgent than a single report suggests.
     */
    public Complaint submit(Complaint complaint) {
        // Severity is decided by the system from category + description. Students
        // should report the facts, not try to guess how serious the issue is.
        complaint.setSeverity(deriveSeverity(complaint.getCategory(), complaint.getDescription()));
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setPriority(derivePriority(complaint.getSeverity()));

        List<Complaint> existingOpen = complaintRepository.findOpenByCategoryAndLocation(
                complaint.getCategory(), complaint.getLocation());

        if (!existingOpen.isEmpty()) {
            Complaint earliest = existingOpen.get(0);
            complaint.setDuplicateOfComplaintId(earliest.getId());
            complaint.setRecurrenceCount(earliest.getRecurrenceCount() + 1);
            // A second report on the same unresolved issue is a signal it
            // needs attention sooner, so escalate priority by one level.
            complaint.setPriority(escalate(complaint.getPriority()));
        }

        Complaint saved = complaintRepository.save(complaint);
        notificationService.notifyRole(Role.ADMIN,
                "New complaint #" + saved.getId() + " submitted: " + saved.getCategory().getDisplayName() + " at " + saved.getLocation(),
                saved.getId());
        return saved;
    }

    private Severity deriveSeverity(Category category, String description) {
        String text = ((category == null ? "" : category.name()) + " " + (description == null ? "" : description)).toLowerCase();
        if (text.matches(".*(fire|smoke|spark|shock|electric shock|exposed wire|short circuit|gas leak|flood|burst pipe|danger|injury|emergency).*")) {
            return Severity.CRITICAL;
        }
        if (text.matches(".*(leak|leaking|broken|unsafe|outage|power cut|water shortage|security|door|ceiling|fan|ac|internet down|network down).*")) {
            return Severity.HIGH;
        }
        if (category == Category.ELECTRICAL || category == Category.PLUMBING) return Severity.HIGH;
        if (category == Category.FURNITURE || category == Category.CLASSROOM_EQUIPMENT || category == Category.NETWORK_IT) return Severity.MEDIUM;
        return Severity.LOW;
    }

    private Priority derivePriority(Severity severity) {
        return switch (severity) {
            case LOW -> Priority.LOW;
            case MEDIUM -> Priority.NORMAL;
            case HIGH -> Priority.HIGH;
            case CRITICAL -> Priority.URGENT;
        };
    }

    private Priority escalate(Priority priority) {
        return switch (priority) {
            case LOW -> Priority.NORMAL;
            case NORMAL -> Priority.HIGH;
            case HIGH -> Priority.URGENT;
            case URGENT -> Priority.URGENT;
        };
    }

    public Complaint getByIdOrThrow(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + id));
    }

    public List<Complaint> findAll() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Complaint> findByStudent(User student) {
        return complaintRepository.findByStudentOrderByCreatedAtDesc(student);
    }

    public List<Complaint> findByStaff(User staff) {
        return complaintRepository.findByAssignedStaffOrderByCreatedAtDesc(staff);
    }

    /**
     * Admin assigns a complaint to a staff member and optionally overrides
     * priority. Notifies the assigned staff member.
     */
    public Complaint assign(Long complaintId, User staff, Priority priority) {
        Complaint complaint = getByIdOrThrow(complaintId);
        complaint.setAssignedStaff(staff);
        if (priority != null) {
            complaint.setPriority(priority);
        }
        complaint.setStatus(ComplaintStatus.ASSIGNED);
        Complaint saved = complaintRepository.save(complaint);
        notificationService.notify(staff,
                "New complaint assigned: #" + saved.getId() + " - " + saved.getCategory().getDisplayName(),
                saved.getId());
        return saved;
    }

    public Complaint markInProgress(Long complaintId, String remarks) {
        Complaint complaint = getByIdOrThrow(complaintId);
        if (complaint.getStatus() != ComplaintStatus.ASSIGNED && complaint.getStatus() != ComplaintStatus.REOPENED) {
            throw new IllegalStateException("This complaint cannot be accepted in its current state.");
        }
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        if (remarks != null && !remarks.isBlank()) {
            complaint.setStaffRemarks(remarks);
        }
        return complaintRepository.save(complaint);
    }

    /**
     * Staff marks the issue resolved with remarks and optional evidence photo.
     * Notifies the student to verify the fix.
     */
    public Complaint markResolved(Long complaintId, String remarks, String evidencePath) {
        Complaint complaint = getByIdOrThrow(complaintId);
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setStaffRemarks(remarks);
        complaint.setResolutionEvidencePath(evidencePath);
        complaint.setResolvedAt(LocalDateTime.now());
        Complaint saved = complaintRepository.save(complaint);
        notificationService.notify(saved.getStudent(),
                "Complaint #" + saved.getId() + " has been resolved. Please verify the fix and give a rating.",
                saved.getId());
        notificationService.notifyRole(Role.ADMIN,
                "Complaint #" + saved.getId() + " was resolved by " + saved.getAssignedStaff().getFullName() + ". Student verification is pending.",
                saved.getId());
        return saved;
    }

    /**
     * Student confirms the fix worked, closing the complaint with feedback.
     */
    public Complaint verify(Long complaintId, Integer rating, String comment) {
        Complaint complaint = getByIdOrThrow(complaintId);
        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            throw new IllegalStateException("Only resolved complaints can be verified.");
        }
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be between 1 and 5.");
        complaint.setStatus(ComplaintStatus.VERIFIED);
        complaint.setFeedbackRating(rating);
        complaint.setFeedbackComment(comment);
        Complaint saved = complaintRepository.save(complaint);
        notificationService.notifyRole(Role.ADMIN,
                "Complaint #" + saved.getId() + " was verified by the student and is now closed. Rating: " + rating + "/5.",
                saved.getId());
        if (saved.getAssignedStaff() != null) {
            notificationService.notify(saved.getAssignedStaff(),
                    "Complaint #" + saved.getId() + " was verified by the student and is now closed.",
                    saved.getId());
        }
        return saved;
    }

    /**
     * Student rejects the resolution (issue not actually fixed), reopening it
     * and notifying the previously assigned staff member.
     */
    public Complaint reopen(Long complaintId, String reason) {
        Complaint complaint = getByIdOrThrow(complaintId);
        complaint.setStatus(ComplaintStatus.REOPENED);
        complaint.setFeedbackComment(reason);
        Complaint saved = complaintRepository.save(complaint);
        if (saved.getAssignedStaff() != null) {
            notificationService.notify(saved.getAssignedStaff(),
                    "Complaint #" + saved.getId() + " was reopened by the student: " + reason,
                    saved.getId());
        }
        notificationService.notifyRole(Role.ADMIN,
                "Complaint #" + saved.getId() + " was reopened by the student: " + reason,
                saved.getId());
        return saved;
    }

    public Complaint reject(Long complaintId, String reason) {
        Complaint complaint = getByIdOrThrow(complaintId);
        complaint.setStatus(ComplaintStatus.REJECTED);
        complaint.setStaffRemarks(reason);
        Complaint saved = complaintRepository.save(complaint);
        notificationService.notify(saved.getStudent(),
                "Your complaint #" + saved.getId() + " was rejected: " + reason,
                saved.getId());
        return saved;
    }


    public Complaint updateByStudent(Long complaintId, Category category, String location, String description, String imagePath) {
        Complaint complaint = getByIdOrThrow(complaintId);
        if (complaint.getStatus() == ComplaintStatus.RESOLVED || complaint.getStatus() == ComplaintStatus.VERIFIED ||
                complaint.getStatus() == ComplaintStatus.REJECTED || complaint.getStatus() == ComplaintStatus.CANCELLED) {
            throw new IllegalStateException("This complaint can no longer be edited.");
        }
        complaint.setCategory(category);
        complaint.setLocation(location);
        complaint.setDescription(description);
        if (imagePath != null) complaint.setImagePath(imagePath);
        complaint.setSeverity(deriveSeverity(category, description));
        complaint.setPriority(derivePriority(complaint.getSeverity()));
        return complaintRepository.save(complaint);
    }

    public Complaint cancelByStudent(Long complaintId) {
        Complaint complaint = getByIdOrThrow(complaintId);
        if (complaint.getStatus() == ComplaintStatus.VERIFIED || complaint.getStatus() == ComplaintStatus.REJECTED ||
                complaint.getStatus() == ComplaintStatus.CANCELLED) {
            throw new IllegalStateException("This complaint cannot be cancelled.");
        }
        complaint.setStatus(ComplaintStatus.CANCELLED);
        Complaint saved = complaintRepository.save(complaint);
        notificationService.notifyRole(Role.ADMIN, "Complaint #" + saved.getId() + " was cancelled by the student.", saved.getId());
        if (saved.getAssignedStaff() != null) {
            notificationService.notify(saved.getAssignedStaff(), "Complaint #" + saved.getId() + " was cancelled by the student.", saved.getId());
        }
        return saved;
    }

    public Complaint linkDuplicate(Long duplicateId, Long mainId) {
        if (duplicateId.equals(mainId)) throw new IllegalArgumentException("A complaint cannot be a duplicate of itself.");
        Complaint duplicate = getByIdOrThrow(duplicateId);
        Complaint main = getByIdOrThrow(mainId);
        duplicate.setDuplicateOfComplaintId(main.getId());
        duplicate.setRecurrenceCount(Math.max(1, main.getRecurrenceCount() + 1));
        Complaint saved = complaintRepository.save(duplicate);
        notificationService.notify(saved.getStudent(), "Your complaint #" + saved.getId() + " has been linked to the existing issue #" + main.getId() + ".", saved.getId());
        return saved;
    }

    public long countByStatus(ComplaintStatus status) {
        return complaintRepository.countByStatus(status);
    }
}
