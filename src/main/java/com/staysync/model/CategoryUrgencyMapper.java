package com.staysync.model;

/**
 * Centralized mapping defining system-assigned complaint urgency by category.
 * Enforces hostel SLA policies and prevents arbitrary High-urgency inflation by students.
 *
 * Approved Mapping Policy:
 *  - Safety & Student Welfare      -> High   (Harassment, personal safety, emergency)
 *  - Food & Mess                   -> High   (Dining contamination, hygiene, mass meal sanitation)
 *  - Washroom & Hygiene            -> Medium (Sanitation, plumbing, drainage)
 *  - Electrical & Room Maintenance -> Medium (Fans, lights, switches, room fixtures)
 *  - Water & Common Facilities     -> Low    (Coolers, common hall facilities)
 */
public final class CategoryUrgencyMapper {

    private CategoryUrgencyMapper() {
        // Utility class
    }

    /**
     * Determines the automatic system-assigned urgency for a given category name.
     *
     * @param categoryName category name (case-insensitive)
     * @return system-assigned {@link Urgency}
     */
    public static Urgency mapCategoryNameToUrgency(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Urgency.MEDIUM;
        }

        String normalized = categoryName.trim().toLowerCase();

        // 1. Safety & Student Welfare -> High (Personal safety, harassment, ragging)
        if (normalized.contains("safety") || normalized.contains("welfare") || normalized.contains("harassment") || normalized.contains("security")) {
            return Urgency.HIGH;
        }

        // 2. Food & Mess -> High (Mass meal sanitation, widespread dining contamination)
        if (normalized.contains("food") || normalized.contains("mess") || normalized.contains("dining")) {
            return Urgency.HIGH;
        }

        // 3. Washroom & Hygiene -> Medium (Sanitation, plumbing, drainage)
        if (normalized.contains("washroom") || normalized.contains("hygiene") || normalized.contains("sanitation")) {
            return Urgency.MEDIUM;
        }

        // 4. Electrical & Room Maintenance -> Medium (Fans, lights, switches, room fixtures)
        if (normalized.contains("electrical") || normalized.contains("maintenance") || normalized.contains("room")) {
            return Urgency.MEDIUM;
        }

        // 5. Water & Common Facilities -> Low (Coolers, common hall facilities)
        if (normalized.contains("water") || normalized.contains("common")) {
            return Urgency.LOW;
        }

        // Safe fallback for unmapped categories
        return Urgency.MEDIUM;
    }

    /**
     * Maps a Category entity to its system-assigned Urgency.
     */
    public static Urgency getUrgency(Category category) {
        if (category == null) return Urgency.MEDIUM;
        return mapCategoryNameToUrgency(category.categoryName());
    }
}
