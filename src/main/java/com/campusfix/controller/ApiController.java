package com.campusfix.controller;

import com.campusfix.model.*;
import com.campusfix.repository.AnalyticsRepository;
import com.campusfix.repository.ComplaintRepository;
import com.campusfix.repository.ComplaintUpdateRepository;
import com.campusfix.repository.NotificationRepository;
import com.campusfix.service.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final UserService users;
    private final ComplaintService cs;
    private final ComplaintUpdateService cus;
    private final NotificationService ns;
    private final FileStorageService fs;
    private final AnalyticsRepository ar;
    private final ComplaintRepository complaintRepository;
    private final ComplaintUpdateRepository updateRepository;
    private final NotificationRepository notificationRepository;

    public ApiController(UserService u, ComplaintService c, ComplaintUpdateService cu,
                         NotificationService n, FileStorageService f, AnalyticsRepository a, ComplaintRepository cr, ComplaintUpdateRepository ur, NotificationRepository nr) {
        users = u; cs = c; cus = cu; ns = n; fs = f; ar = a; complaintRepository = cr; updateRepository = ur; notificationRepository = nr;
    }

    // ---------------- AUTH ----------------
    @GetMapping("/me")
    public Map<String, Object> me(Authentication a) { return user(current(a)); }

    @PostMapping("/auth/register")
    public Map<String, Object> register(@RequestBody Map<String, String> b) {
        return user(users.register(b.get("username"), b.get("password"), b.get("fullName"),
                b.get("email"), Role.STUDENT, b.get("department")));
    }

    // ---------------- STUDENT CRUD ----------------
    @GetMapping("/student/dashboard")
    public Map<String, Object> sd(Authentication a) {
        User u = current(a);
        return Map.of("complaints", cs.findByStudent(u).stream().map(this::comp).toList(),
                "notificationCount", ns.getUnreadCount(u));
    }

    @GetMapping("/student/complaints")
    public List<Map<String, Object>> sc(Authentication a) {
        return cs.findByStudent(current(a)).stream().map(this::comp).toList();
    }

    @GetMapping("/student/complaints/{id}")
    public Map<String, Object> sdetail(@PathVariable Long id, Authentication a) {
        return comp(owned(id, current(a)));
    }

    @PostMapping(value = "/student/complaints", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> submit(Authentication a,
                                      @RequestParam Category category,
                                      @RequestParam String location,
                                      @RequestParam String description,
                                      @RequestParam(required = false) MultipartFile image) {
        Complaint c = new Complaint();
        c.setStudent(current(a));
        c.setCategory(category);
        c.setLocation(location);
        c.setDescription(description);
        c.setImagePath(fs.store(image));
        return comp(cs.submit(c));
    }

    @PutMapping(value = "/student/complaints/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> updateComplaint(@PathVariable Long id, Authentication a,
                                               @RequestParam Category category,
                                               @RequestParam String location,
                                               @RequestParam String description,
                                               @RequestParam(required = false) MultipartFile image) {
        Complaint old = owned(id, current(a));
        String imagePath = fs.store(image);
        return comp(cs.updateByStudent(old.getId(), category, location, description, imagePath));
    }

    @DeleteMapping("/student/complaints/{id}")
    public Map<String, Object> cancelComplaint(@PathVariable Long id, Authentication a) {
        owned(id, current(a));
        return comp(cs.cancelByStudent(id));
    }

    @PutMapping("/student/complaints/{id}/verification")
    public Map<String, Object> verify(@PathVariable Long id, @RequestBody Map<String, Object> b, Authentication a) {
        owned(id, current(a));
        return comp(cs.verify(id, Integer.parseInt(String.valueOf(b.get("rating"))),
                String.valueOf(b.getOrDefault("comment", ""))));
    }

    @PutMapping("/student/complaints/{id}/reopen")
    public Map<String, Object> reopen(@PathVariable Long id, @RequestBody Map<String, String> b, Authentication a) {
        owned(id, current(a));
        return comp(cs.reopen(id, b.getOrDefault("reason", "Issue is not fixed")));
    }

    // Backward-compatible aliases for the existing UI/workflow.
    @PostMapping("/student/complaints/{id}/verify")
    public Map<String, Object> verifyLegacy(@PathVariable Long id, @RequestBody Map<String, Object> b, Authentication a) {
        return verify(id, b, a);
    }
    @PostMapping("/student/complaints/{id}/reopen")
    public Map<String, Object> reopenLegacy(@PathVariable Long id, @RequestBody Map<String, String> b, Authentication a) {
        return reopen(id, b, a);
    }

    // ---------------- STAFF CRUD ----------------
    @GetMapping("/staff/dashboard")
    public Map<String, Object> st(Authentication a) {
        User u = current(a);
        return Map.of("complaints", cs.findByStaff(u).stream().map(this::comp).toList(),
                "notificationCount", ns.getUnreadCount(u));
    }

    @GetMapping("/staff/complaints")
    public List<Map<String, Object>> staffComplaints(Authentication a) {
        return cs.findByStaff(current(a)).stream().map(this::comp).toList();
    }

    @GetMapping("/staff/complaints/{id}")
    public Map<String, Object> std(@PathVariable Long id, Authentication a) {
        Complaint c = cs.getByIdOrThrow(id);
        staffCheck(c, current(a));
        return comp(c);
    }

    @PutMapping("/staff/complaints/{id}/status")
    public Map<String, Object> staffStatus(@PathVariable Long id, @RequestBody Map<String, String> b, Authentication a) {
        Complaint c = cs.getByIdOrThrow(id);
        staffCheck(c, current(a));
        String status = b.getOrDefault("status", "IN_PROGRESS");
        if ("IN_PROGRESS".equals(status)) return comp(cs.markInProgress(id, b.getOrDefault("remarks", "Work started")));
        throw new IllegalArgumentException("Use the resolution endpoint to mark a complaint RESOLVED.");
    }

    @PutMapping(value = "/staff/complaints/{id}/resolution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> staffResolution(@PathVariable Long id,
                                                @RequestParam String remarks,
                                                @RequestParam MultipartFile evidence,
                                                Authentication a) {
        Complaint c = cs.getByIdOrThrow(id);
        staffCheck(c, current(a));
        if (evidence == null || evidence.isEmpty()) throw new IllegalArgumentException("Resolution proof image is required.");
        return comp(cs.markResolved(id, remarks, fs.store(evidence)));
    }

    @GetMapping("/staff/complaints/{complaintId}/updates")
    public List<Map<String, Object>> getUpdates(@PathVariable Long complaintId, Authentication a) {
        return cus.findForComplaint(complaintId, current(a)).stream().map(this::update).toList();
    }

    @PostMapping("/staff/complaints/{complaintId}/updates")
    public Map<String, Object> createUpdate(@PathVariable Long complaintId,
                                             @RequestBody Map<String, Object> b, Authentication a) {
        boolean draft = Boolean.parseBoolean(String.valueOf(b.getOrDefault("draft", true)));
        return update(cus.create(complaintId, current(a), String.valueOf(b.get("description")), draft));
    }

    @PutMapping("/staff/updates/{id}")
    public Map<String, Object> editUpdate(@PathVariable Long id, @RequestBody Map<String, String> b, Authentication a) {
        return update(cus.update(id, current(a), b.get("description")));
    }

    @DeleteMapping("/staff/updates/{id}")
    public Map<String, Object> deleteDraftUpdate(@PathVariable Long id, Authentication a) {
        cus.delete(id, current(a));
        return Map.of("success", true);
    }

    // Existing workflow aliases.
    @PostMapping("/staff/complaints/{id}/accept")
    public Map<String, Object> acceptLegacy(@PathVariable Long id, @RequestBody Map<String, String> b, Authentication a) {
        return staffStatus(id, Map.of("status", "IN_PROGRESS", "remarks", b.getOrDefault("progress", "Work started")), a);
    }
    @PostMapping(value = "/staff/complaints/{id}/resolve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> resolveLegacy(@PathVariable Long id, @RequestParam String remarks,
                                              @RequestParam MultipartFile evidence, Authentication a) {
        return staffResolution(id, remarks, evidence, a);
    }

    // ---------------- ADMIN CRUD ----------------
    @GetMapping("/admin/dashboard")
    public Map<String, Object> ad(Authentication a) {
        return Map.of("byStatus", ar.countByStatus(), "byCategory", ar.countByCategory(),
                "avgRating", Objects.requireNonNullElse(ar.averageFeedbackRating(), 0),
                "avgResolutionHours", Objects.requireNonNullElse(ar.averageResolutionHours(), 0),
                "recurringCount", ar.countRecurringIssues(),
                "unassignedCount", ar.countUnassigned(),
                "recentComplaints", cs.findAll().stream().limit(10).map(this::comp).toList());
    }

    @GetMapping("/admin/complaints")
    public List<Map<String, Object>> ac(@RequestParam(required = false) ComplaintStatus status,
                                        @RequestParam(required = false) Boolean assigned,
                                        @RequestParam(required = false) Boolean recurring,
                                        Authentication a) {
        List<Complaint> list = cs.findAll();
        if (status != null) list = list.stream().filter(x -> x.getStatus() == status).toList();
        if (assigned != null) list = list.stream().filter(x -> (x.getAssignedStaff() != null) == assigned).toList();
        if (recurring != null) list = list.stream().filter(x -> recurring ? (x.getDuplicateOfComplaintId() != null || x.getRecurrenceCount() > 0) : (x.getDuplicateOfComplaintId() == null && x.getRecurrenceCount() == 0)).toList();
        return list.stream().map(this::comp).toList();
    }

    @GetMapping("/admin/staff")
    public List<Map<String, Object>> staff() { return users.findStaff().stream().map(this::user).toList(); }

    @PostMapping("/admin/staff")
    public Map<String, Object> createStaff(@RequestBody Map<String, String> b) {
        return user(users.register(b.get("username"), b.get("password"), b.get("fullName"), b.get("email"), Role.STAFF, b.get("department")));
    }

    @PutMapping("/admin/staff/{id}")
    public Map<String, Object> updateStaff(@PathVariable Long id, @RequestBody Map<String, String> b) {
        return user(users.updateStaff(id, b.get("fullName"), b.get("email"), b.get("department")));
    }

    @PutMapping("/admin/staff/{id}/deactivate")
    public Map<String, Object> deactivateStaff(@PathVariable Long id) {
        return user(users.deactivateStaff(id));
    }

    @DeleteMapping("/admin/staff/{id}")
    public ResponseEntity<?> deleteInactiveStaff(@PathVariable Long id) {
        User staff = users.findStaff().stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Staff not found."));
        users.deleteInactiveStaff(id, complaintRepository.existsByAssignedStaff(staff),
                updateRepository.existsByStaff(staff), notificationRepository.existsByUser(staff));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody Map<String, String> body) {
        users.requestPasswordReset(body.get("email"));
        return Map.of("success", true, "message", "If an account matches that email, password reset instructions have been sent.");
    }

    @PostMapping("/auth/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> body) {
        users.resetPassword(body.get("token"), body.get("newPassword"));
        return Map.of("success", true);
    }

    @PutMapping("/admin/complaints/{id}/assignment")
    public Map<String, Object> assign(@PathVariable Long id, @RequestBody Map<String, String> b) {
        Long sid = Long.valueOf(b.get("staffId"));
        User s = users.findStaff().stream().filter(x -> x.getId().equals(sid)).findFirst().orElseThrow();
        return comp(cs.assign(id, s, null));
    }

    @PutMapping("/admin/complaints/{id}/duplicate-of/{mainId}")
    public Map<String, Object> linkDuplicate(@PathVariable Long id, @PathVariable Long mainId) {
        return comp(cs.linkDuplicate(id, mainId));
    }

    @PutMapping("/admin/complaints/{id}/rejection")
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody Map<String, String> b) {
        return comp(cs.reject(id, b.getOrDefault("reason", "Rejected by admin")));
    }

    // Existing admin aliases.
    @PostMapping("/admin/complaints/{id}/assign")
    public Map<String, Object> assignLegacy(@PathVariable Long id, @RequestBody Map<String, String> b) { return assign(id, b); }
    @PostMapping("/admin/complaints/{id}/reject")
    public Map<String, Object> rejectLegacy(@PathVariable Long id, @RequestBody Map<String, String> b) { return reject(id, b); }

    // ---------------- NOTIFICATIONS ----------------
    @GetMapping("/notifications")
    public Map<String, Object> notifications(Authentication a) {
        User u = current(a);
        return Map.of("notifications", ns.getForUser(u).stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId()); m.put("message", n.getMessage());
            m.put("relatedComplaintId", n.getRelatedComplaintId()); m.put("read", n.isRead()); m.put("createdAt", n.getCreatedAt());
            return m;
        }).toList(), "unreadCount", ns.getUnreadCount(u));
    }

    @DeleteMapping("/notifications/{id}")
    public Map<String, Object> deleteNotification(@PathVariable Long id, Authentication a) {
        ns.deleteForUser(id, current(a));
        return Map.of("success", true);
    }

    @PutMapping("/notifications/read")
    public Map<String, Object> read(Authentication a) { ns.markAllRead(current(a)); return Map.of("success", true); }
    @PostMapping("/notifications/read")
    public Map<String, Object> readLegacy(Authentication a) { return read(a); }

    // ---------------- PROFILE ----------------
    @GetMapping("/profile")
    public Map<String, Object> profile(Authentication a) { return user(current(a)); }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, String> b, Authentication a) {
        return user(users.updateProfile(current(a), b.get("username"), b.get("fullName"), b.get("email"), b.get("department")));
    }

    @PutMapping("/profile/password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> b, Authentication a) {
        users.changePassword(current(a), b.get("currentPassword"), b.get("newPassword"));
        return Map.of("success", true);
    }

    @PutMapping(value = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> profilePhoto(@RequestParam MultipartFile photo, Authentication a) {
        if (photo == null || photo.isEmpty()) throw new IllegalArgumentException("Profile photo is required.");
        User u = current(a);
        u.setProfilePhotoPath(fs.store(photo));
        return user(users.save(u));
    }

    private User current(Authentication a) { return users.getByUsernameOrThrow(a.getName()); }

    private User ownedUser(Authentication a) { return current(a); }

    private Complaint owned(Long id, User u) {
        Complaint c = cs.getByIdOrThrow(id);
        if (!c.getStudent().getId().equals(u.getId())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return c;
    }

    private void staffCheck(Complaint c, User u) {
        if (c.getAssignedStaff() == null || !c.getAssignedStaff().getId().equals(u.getId()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private Map<String, Object> user(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId()); m.put("username", u.getUsername()); m.put("fullName", u.getFullName());
        m.put("email", u.getEmail()); m.put("role", u.getRole()); m.put("department", u.getDepartment());
        m.put("enabled", u.isEnabled()); m.put("profilePhotoPath", u.getProfilePhotoPath());
        return m;
    }

    private Map<String, Object> comp(Complaint c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId()); m.put("student", user(c.getStudent()));
        m.put("category", c.getCategory()); m.put("categoryName", c.getCategory().getDisplayName());
        m.put("location", c.getLocation()); m.put("description", c.getDescription());
        m.put("severity", c.getSeverity()); m.put("priority", c.getPriority()); m.put("status", c.getStatus());
        m.put("imagePath", c.getImagePath()); m.put("assignedStaff", c.getAssignedStaff() == null ? null : user(c.getAssignedStaff()));
        m.put("duplicateOfComplaintId", c.getDuplicateOfComplaintId()); m.put("recurrenceCount", c.getRecurrenceCount());
        m.put("staffRemarks", c.getStaffRemarks()); m.put("resolutionEvidencePath", c.getResolutionEvidencePath());
        m.put("feedbackRating", c.getFeedbackRating()); m.put("feedbackComment", c.getFeedbackComment());
        m.put("createdAt", c.getCreatedAt()); m.put("updatedAt", c.getUpdatedAt()); m.put("resolvedAt", c.getResolvedAt());
        return m;
    }

    private Map<String, Object> update(ComplaintUpdate u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId()); m.put("complaintId", u.getComplaint().getId());
        m.put("staffId", u.getStaff().getId()); m.put("description", u.getDescription());
        m.put("draft", u.isDraft()); m.put("createdAt", u.getCreatedAt()); m.put("updatedAt", u.getUpdatedAt());
        return m;
    }
}
