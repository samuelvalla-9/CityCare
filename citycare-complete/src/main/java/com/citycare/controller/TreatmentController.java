package com.citycare.controller;

import com.citycare.dto.request.TreatmentRequest;
import com.citycare.dto.response.ApiResponse;
import com.citycare.entity.Treatment;
import com.citycare.exception.ResourceNotFoundException;
import com.citycare.repository.UserRepository;
import com.citycare.service.impl.TreatmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================================
 * TreatmentController.java  –  Medical Treatments (Doctor/Nurse)
 * ============================================================
 *
 * Covers Step 4 of the workflow:
 *   Doctor/Nurse assigns treatments to admitted patients.
 *
 * All /treatments/** endpoints require DOCTOR or NURSE role
 * (enforced by SecurityConfig – returns 403 for other roles).
 *
 * The logged-in staff member's ID is taken from the JWT token via
 * @AuthenticationPrincipal – this means the 'assignedBy' field
 * is always the actual logged-in doctor/nurse, never spoofable.
 *
 * ============================================================
 * POSTMAN TESTING GUIDE
 * ============================================================
 *
 * PREREQUISITE: A patient must be ADMITTED (not DISCHARGED).
 *   → Get patientId from: GET /patients  or  GET /patients/status/ADMITTED
 *
 * ──────────────────────────────────────────────────────────────
 * [STEP 4] POST /treatments  →  Assign Treatment to Patient
 * ──────────────────────────────────────────────────────────────
 * ROLE   : DOCTOR or NURSE (use their JWT token)
 * METHOD : POST
 * URL    : http://localhost:8080/api/treatments
 * HEADERS:
 *   Content-Type:  application/json
 *   Authorization: Bearer <DOCTOR_or_NURSE_TOKEN>
 *
 * BODY EXAMPLE 1 – Doctor prescribes medication:
 * {
 *   "patientId": 1,
 *   "description": "Administer IV antibiotics every 8 hours for 5 days",
 *   "medicationName": "Amoxicillin",
 *   "dosage": "500mg IV every 8 hours"
 * }
 *
 * BODY EXAMPLE 2 – Nurse assigns monitoring:
 * {
 *   "patientId": 1,
 *   "description": "Monitor blood pressure every 2 hours. Record in chart.",
 *   "medicationName": null,
 *   "dosage": null
 * }
 *
 * BODY EXAMPLE 3 – Doctor prescribes procedure:
 * {
 *   "patientId": 1,
 *   "description": "X-ray of left leg. Suspected fracture at tibia.",
 *   "medicationName": "N/A",
 *   "dosage": "N/A"
 * }
 *
 * EXPECTED RESPONSE (201 Created):
 * {
 *   "success": true,
 *   "message": "Treatment assigned",
 *   "data": {
 *     "treatmentId": 1,
 *     "patient": { "patientId": 1 },
 *     "assignedBy": { "name": "Dr. Priya", "role": "DOCTOR" },
 *     "description": "Administer IV antibiotics every 8 hours",
 *     "medicationName": "Amoxicillin",
 *     "dosage": "500mg IV every 8 hours",
 *     "startDate": "2024-10-15",
 *     "status": "ONGOING"
 *   }
 * }
 *
 * ERROR CASES:
 *   → 400 if patient is DISCHARGED:
 *     { "message": "Cannot assign treatment to patient 1 – they have already been discharged." }
 *   → 403 if non-DOCTOR/NURSE role tries this
 *
 * ──────────────────────────────────────────────────────────────
 * GET /treatments/patient/{patientId}  →  All Treatments for a Patient
 * ──────────────────────────────────────────────────────────────
 * ROLE   : DOCTOR or NURSE
 * METHOD : GET
 * URL    : http://localhost:8080/api/treatments/patient/1
 *         (Replace 1 with actual patientId)
 * HEADERS:
 *   Authorization: Bearer <DOCTOR_or_NURSE_TOKEN>
 *
 * NO BODY needed.
 *
 * EXPECTED RESPONSE (200 OK):
 * {
 *   "success": true,
 *   "message": "Treatments for patient",
 *   "data": [
 *     { "treatmentId": 1, "description": "IV antibiotics", "status": "ONGOING" },
 *     { "treatmentId": 2, "description": "Monitor BP", "status": "ONGOING" }
 *   ]
 * }
 *
 * ──────────────────────────────────────────────────────────────
 * PATCH /treatments/{id}/status  →  Complete or Cancel Treatment
 * ──────────────────────────────────────────────────────────────
 * ROLE   : DOCTOR or NURSE
 * METHOD : PATCH
 * URL    : http://localhost:8080/api/treatments/1/status?status=COMPLETED
 *         (Replace 1 with treatmentId)
 * HEADERS:
 *   Authorization: Bearer <DOCTOR_or_NURSE_TOKEN>
 *
 * NO BODY – status is a query parameter.
 *
 * VALID STATUS VALUES:
 *   ONGOING   – default when created
 *   COMPLETED – treatment finished successfully
 *   CANCELLED – treatment no longer needed / discontinued
 *
 * EXAMPLE URLs:
 *   PATCH /api/treatments/1/status?status=COMPLETED
 *   PATCH /api/treatments/1/status?status=CANCELLED
 *
 * WHAT HAPPENS: endDate is automatically set to today.
 *
 * EXPECTED RESPONSE (200 OK):
 * {
 *   "success": true,
 *   "message": "Treatment status updated",
 *   "data": {
 *     "treatmentId": 1,
 *     "status": "COMPLETED",
 *     "endDate": "2024-10-20"
 *   }
 * }
 *
 * ──────────────────────────────────────────────────────────────
 * GET /treatments/mine  →  My Assigned Treatments
 * ──────────────────────────────────────────────────────────────
 * ROLE   : DOCTOR or NURSE
 * METHOD : GET
 * URL    : http://localhost:8080/api/treatments/mine
 * HEADERS:
 *   Authorization: Bearer <DOCTOR_or_NURSE_TOKEN>
 *
 * NO BODY. Returns all treatments assigned by the logged-in staff member.
 * Useful for "my patients" view in the doctor/nurse dashboard.
 *
 * ============================================================
 */
@RestController
@RequestMapping("/treatments")
@RequiredArgsConstructor
@Tag(name = "4. Treatments", description = "Step 4: Doctor/Nurse assigns treatments and updates patient status.")
public class TreatmentController {

    private final TreatmentService treatmentService;
    private final UserRepository userRepository;

    // ── STEP 4: Assign treatment ──────────────────────────────────────────────

    @PostMapping
    @Operation(
        summary = "[DOCTOR/NURSE] Assign Treatment to Patient – Step 4",
        description = """
            POSTMAN: POST http://localhost:8080/api/treatments
            Header: Authorization: Bearer <DOCTOR_or_NURSE_TOKEN>
            Body:
            {
              "patientId": 1,
              "description": "Administer IV antibiotics every 8 hours for 5 days",
              "medicationName": "Amoxicillin",
              "dosage": "500mg IV every 8 hours"
            }
            → 'medicationName' and 'dosage' are optional.
            → assignedBy is automatically the logged-in doctor/nurse.
            → Treatment starts with status ONGOING.
            """
    )
    public ResponseEntity<ApiResponse<Treatment>> assign(
            @Valid @RequestBody TreatmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long staffId = resolveUserId(userDetails);
        Treatment treatment = treatmentService.assignTreatment(staffId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Treatment assigned", treatment));
    }

    // ── Get all treatments for a specific patient ─────────────────────────────

    @GetMapping("/patient/{patientId}")
    @Operation(
        summary = "[DOCTOR/NURSE] Get All Treatments for a Patient",
        description = """
            POSTMAN: GET http://localhost:8080/api/treatments/patient/1
            Header: Authorization: Bearer <DOCTOR_or_NURSE_TOKEN>
            No body needed. Replace '1' with actual patientId.
            """
    )
    public ResponseEntity<ApiResponse<List<Treatment>>> getForPatient(
            @PathVariable Long patientId) {

        return ResponseEntity.ok(
                ApiResponse.ok("Treatments for patient",
                        treatmentService.getForPatient(patientId)));
    }

    // ── Update treatment status ───────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    @Operation(
        summary = "[DOCTOR/NURSE] Update Treatment Status (Complete or Cancel)",
        description = """
            POSTMAN: PATCH http://localhost:8080/api/treatments/1/status?status=COMPLETED
            Header: Authorization: Bearer <DOCTOR_or_NURSE_TOKEN>
            No body needed. Status is a query parameter.
            
            Valid values: ONGOING | COMPLETED | CANCELLED
            
            Example URLs:
            PATCH /api/treatments/1/status?status=COMPLETED
            PATCH /api/treatments/2/status?status=CANCELLED
            
            On COMPLETED or CANCELLED → endDate is set to today automatically.
            """
    )
    public ResponseEntity<ApiResponse<Treatment>> updateStatus(
            @PathVariable Long id,
            @RequestParam Treatment.Status status) {

        Treatment treatment = treatmentService.updateStatus(id, status);
        return ResponseEntity.ok(ApiResponse.ok("Treatment status updated", treatment));
    }

    // ── Get all treatments assigned by the logged-in staff member ────────────

    @GetMapping("/mine")
    @Operation(
        summary = "[DOCTOR/NURSE] View Treatments I Assigned",
        description = """
            POSTMAN: GET http://localhost:8080/api/treatments/mine
            Header: Authorization: Bearer <DOCTOR_or_NURSE_TOKEN>
            No body needed.
            → Returns only treatments assigned by the logged-in doctor/nurse.
            """
    )
    public ResponseEntity<ApiResponse<List<Treatment>>> getMine(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long staffId = resolveUserId(userDetails);
        return ResponseEntity.ok(
                ApiResponse.ok("Your assigned treatments",
                        treatmentService.getMyAssigned(staffId)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"))
                .getUserId();
    }
}
