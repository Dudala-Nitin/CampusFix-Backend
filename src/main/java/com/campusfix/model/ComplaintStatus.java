package com.campusfix.model;

public enum ComplaintStatus {
    SUBMITTED,      // student submitted, waiting for admin assignment
    ASSIGNED,       // admin assigned to staff
    IN_PROGRESS,    // staff accepted and started work
    RESOLVED,       // staff submitted resolution proof, waiting for student verification
    VERIFIED,       // student confirmed fix; case closed
    REOPENED,       // student rejected resolution
    REJECTED,       // admin rejected complaint
    CANCELLED       // student withdrew/cancelled the complaint
}
