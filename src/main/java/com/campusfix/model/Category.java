package com.campusfix.model;

public enum Category {
    ELECTRICAL("Electrical Fault"),
    PLUMBING("Water Leakage / Plumbing"),
    FURNITURE("Damaged Furniture"),
    CLASSROOM_EQUIPMENT("Classroom Equipment"),
    CLEANLINESS("Cleanliness"),
    NETWORK_IT("Network / IT"),
    OTHER("Other");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
