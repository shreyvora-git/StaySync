package com.staysync.security;

import com.staysync.error.AuthenticationException;
import com.staysync.error.ForbiddenException;
import com.staysync.error.NotFoundException;
import com.staysync.model.Complaint;
import com.staysync.model.Role;

/**
 * Central authorization helper enforcing role restrictions, student complaint ownership,
 * and confidential safety record masking.
 *
 * <p><strong>Confidentiality Masking Rule:</strong>
 * When an unauthorized user attempts to look up a confidential complaint, this service throws
 * {@link NotFoundException} (identical to an invalid ID) to prevent leaking the existence
 * of sensitive safety issues.
 */
public final class Permissions {

    private Permissions() {
        // Utility class
    }

    /**
     * Asserts that a valid authenticated user context is present.
     */
    public static void requireAuthenticated(CurrentUser user) {
        if (user == null) {
            throw new AuthenticationException("Authentication required to perform this action.");
        }
    }

    /**
     * Asserts that the authenticated user possesses the Student role.
     */
    public static void requireStudent(CurrentUser user) {
        requireAuthenticated(user);
        if (user.role() != Role.STUDENT) {
            throw new ForbiddenException("Only registered students can perform this action.");
        }
    }

    /**
     * Asserts that the authenticated user possesses the Admin role.
     */
    public static void requireAdmin(CurrentUser user) {
        requireAuthenticated(user);
        if (user.role() != Role.ADMIN) {
            throw new ForbiddenException("Administrative privileges required for this action.");
        }
    }

    /**
     * Asserts that the authenticated user is an Admin with explicit confidential clearance.
     */
    public static void requireAuthorizedAdmin(CurrentUser user) {
        requireAdmin(user);
        if (!user.canViewConfidential()) {
            throw new ForbiddenException("Confidential access clearance is required for this action.");
        }
    }

    /**
     * Tests whether the authenticated user has permission to read or interact with a complaint.
     */
    public static boolean canAccessComplaint(CurrentUser user, Complaint complaint) {
        if (user == null || complaint == null) {
            return false;
        }

        if (user.isStudent()) {
            return complaint.studentId() == user.userId();
        }

        if (user.isAdmin()) {
            return !complaint.isConfidential() || user.canViewConfidential();
        }

        return false;
    }

    /**
     * Asserts that the user is permitted to view or update the target complaint.
     * Throws {@link NotFoundException} if the complaint is confidential and the user lacks clearance,
     * or if a student attempts to view a complaint belonging to someone else.
     */
    public static void checkComplaintAccess(CurrentUser user, Complaint complaint) {
        requireAuthenticated(user);
        if (complaint == null) {
            throw new NotFoundException("Complaint not found.");
        }

        if (user.isStudent()) {
            if (complaint.studentId() != user.userId()) {
                throw new NotFoundException("Complaint #" + complaint.complaintId() + " not found.");
            }
            return;
        }

        if (user.isAdmin()) {
            if (complaint.isConfidential() && !user.canViewConfidential()) {
                // Confidentiality guard: mask existence with NotFoundException
                throw new NotFoundException("Complaint #" + complaint.complaintId() + " not found.");
            }
            return;
        }

        throw new ForbiddenException("Access denied to complaint #" + complaint.complaintId());
    }

    /**
     * Tests whether the user is authorized to read staff internal notes.
     */
    public static boolean canViewInternalComments(CurrentUser user) {
        return user != null && user.isAdmin();
    }

    /**
     * Asserts that the user is authorized to view or post staff internal notes.
     */
    public static void requireInternalCommentAccess(CurrentUser user) {
        requireAuthenticated(user);
        if (!canViewInternalComments(user)) {
            throw new ForbiddenException("Students are not permitted to view or post internal staff notes.");
        }
    }
}
