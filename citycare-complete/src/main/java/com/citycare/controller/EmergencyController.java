package com.citycare.controller;

import com.citycare.dto.request.DispatchRequest;
import com.citycare.dto.request.EmergencyRequest;
import com.citycare.dto.response.ApiResponse;
import com.citycare.entity.Ambulance;
import com.citycare.entity.Emergency;
import com.citycare.exception.ResourceNotFoundException;
import com.citycare.repository.UserRepository;
import com.citycare.service.impl.EmergencyService;
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
 * EmergencyController.java  –  Emergency Reporting + Dispatch
 * ============================================================
 *
 * Covers Steps 1 and 2 of the full workflow.
 *
 * @AuthenticationPrincipal UserDetails
 *   Spring injects the logged-in user's details (extracted from JWT).
 *   userDetails.getUsername() returns their EMAIL.
 *   We use that email to look up their userId from the DB.
 *   This is safer than accepting userId in the URL (prevents spoofing).
 *
 * ============================================================
 * POSTMAN TESTING – COMPLETE WORKFLOW STEPS 1 & 2
 * ============================================================
 *
 * ── PREREQUISITE ──────────────────────────────────────────────
 * Before testing emergency endpoints:
 *   1. Admin must have added an ambulance: POST /admin/ambulances
 *   2. Citizen must be registered and logged in
 *   3. Dispatcher must be created by Admin and logged in
 *
 * ──────────────────────────────────────────────────────────────
 * [STEP 1] POST /emergencies/report  →  Citizen Reports Emergency
 * ──────────────────────────────────────────────────────────────
 * ROLE   : CITIZEN (use citizen's JWT token)
 * METHOD : POST
 * URL    : http://localhost:8080/api/emergencies/report
 * HEADERS:
 *   Content-Type:  application/json
 *   Authorization: Bearer <CITIZEN_TOKEN>
 *
 * BODY (raw JSON):
 * {
 *   "type": "ACCIDENT",
 *   "location": "Anna Nagar, Chennai - Near Metro Station",
 *   "description": "Road accident, 2 people injured, need immediate help"
 * }
 *
 * VALID TYPE VALUES: ACCIDENT, HEART_ATTACK, FIRE, STROKE, FALL, OTHER
 *
 * EXPECTED RESPONSE (201 Created):
 * {
 *   "success": true,
 *   "message": "Emergency reported. Help is on the way.",
 *   "data": {
 *     "emergencyId": 1,
 *     "type": "ACCIDENT",
 *     "location": "Anna Nagar, Chennai - Near Metro Station",
 *     "status": "REPORTED",
 *     "reportedAt": "2024-10-15T09:30:00"
 *   }
 * }
 *
 * NOTE: emergencyId = 1 (save this – dispatcher needs it to dispatch)
 *
 * ──────────────────────────────────────────────────────────────
 * [STEP 2a] GET /emergencies/pending  →  Dispatcher Views Pending
 * ──────────────────────────────────────────────────────────────
 * ROLE   : DISPATCHER (use dispatcher's JWT token)
 * METHOD : GET
 * URL    : http://localhost:8080/api/emergencies/pending
 * HEADERS:
 *   Authorization: Bearer <DISPATCHER_TOKEN>
 *
 * NO BODY needed.
 *
 * EXPECTED RESPONSE (200 OK):
 * {
 *   "success": true,
 *   "message": "Pending emergencies",
 *   "data": [
 *     {
 *       "emergencyId": 1,
 *       "type": "ACCIDENT",
 *       "location": "Anna Nagar, Chennai",
 *       "status": "REPORTED",
 *       "citizen": { "name": "Ravi Kumar", "phone": "9876543210" }
 *     }
 *   ]
 * }
 *
 * Frontend polls this endpoint every 10-30 seconds to show dispatcher alerts.
 *
 * ──────────────────────────────────────────────────────────────
 * [STEP 2b] GET /emergencies/ambulances/available  →  Check Available Ambulances
 * ──────────────────────────────────────────────────────────────
 * ROLE   : DISPATCHER
 * METHOD : GET
 * URL    : http://localhost:8080/api/emergencies/ambulances/available
 * HEADERS:
 *   Authorization: Bearer <DISPATCHER_TOKEN>
 *
 * NO BODY needed.
 *
 * EXPECTED RESPONSE (200 OK):
 * {
 *   "success": true,
 *   "message": "Available ambulances",
 *   "data": [
 *     { "ambulanceId": 1, "vehicleNumber": "TN-01-AB-1234", "model": "Toyota HiAce", "status": "AVAILABLE" },
 *     { "ambulanceId": 2, "vehicleNumber": "TN-01-CD-5678", "model": "Force Traveller", "status": "AVAILABLE" }
 *   ]
 * }
 *
 * Pick an ambulanceId from this list. Use it in the next step.
 *
 * ──────────────────────────────────────────────────────────────
 * [STEP 2c] POST /emergencies/{emergencyId}/dispatch  →  Dispatcher Assigns Ambulance
 * ──────────────────────────────────────────────────────────────
 * ROLE   : DISPATCHER
 * METHOD : POST
 * URL    : http://localhost:8080/api/emergencies/1/dispatch   ← replace 1 with actual emergencyId
 * HEADERS:
 *   Content-Type:  application/json
 *   Authorization: Bearer <DISPATCHER_TOKEN>
 *
 * BODY (raw JSON):
 * {
 *   "ambulanceId": 1
 * }
 *
 * EXPECTED RESPONSE (200 OK):
 * {
 *   "success": true,
 *   "message": "Ambulance dispatched successfully",
 *   "data": {
 *     "emergencyId": 1,
 *     "status": "DISPATCHED",
 *     "ambulance": { "ambulanceId": 1, "vehicleNumber": "TN-01-AB-1234", "status": "DISPATCHED" },
 *     "dispatcher": { "name": "Dispatcher Kumar" },
 *     "dispatchedAt": "2024-10-15T09:35:00"
 *   }
 * }
 *
 * AFTER THIS:
 *   → Ambulance status is now DISPATCHED (won't appear in available list)
 *   → Emergency status is now DISPATCHED
 *   → Admin can see this in GET /emergencies/dispatched
 *
 * ERROR CASES:
 *   → 400 if ambulance is already DISPATCHED:
 *     { "success": false, "message": "Ambulance TN-01-AB-1234 is not available (current status: DISPATCHED)" }
 *   → 400 if emergency already dispatched:
 *     { "success": false, "message": "Cannot dispatch – emergency is already DISPATCHED." }
 *
 * ──────────────────────────────────────────────────────────────
 * [ADMIN VIEW] GET /emergencies/dispatched  →  Admin Sees What Needs Admission
 * ──────────────────────────────────────────────────────────────
 * ROLE   : ADMIN
 * METHOD : GET
 * URL    : http://localhost:8080/api/emergencies/dispatched
 * HEADERS:
 *   Authorization: Bearer <ADMIN_TOKEN>
 *
 * Returns list of emergencies where ambulance has been dispatched.
 * Admin uses emergencyId from this list to call POST /patients/admit.
 *
 * ──────────────────────────────────────────────────────────────
 * [CITIZEN] GET /emergencies/my  →  View Own Emergency History
 * ──────────────────────────────────────────────────────────────
 * ROLE   : CITIZEN
 * METHOD : GET
 * URL    : http://localhost:8080/api/emergencies/my
 * HEADERS:
 *   Authorization: Bearer <CITIZEN_TOKEN>
 *
 * NO BODY. Returns all emergencies reported by this citizen.
 *
 * ──────────────────────────────────────────────────────────────
 * [ANY] GET /emergencies/{id}  →  Get One Emergency Detail
 * ──────────────────────────────────────────────────────────────
 * METHOD : GET
 * URL    : http://localhost:8080/api/emergencies/1
 * HEADERS:
 *   Authorization: Bearer <ANY_VALID_TOKEN>
 *
 * ============================================================
 */
@RestController
@RequestMapping("/emergencies")
@RequiredArgsConstructor
@Tag(name = "2. Emergency", description = "Step 1: Citizen reports. Step 2: Dispatcher views + assigns ambulance.")
public class EmergencyController {

    private final EmergencyService emergencyService;
    private final UserRepository userRepository;

    // ── STEP 1: Citizen reports an emergency ─────────────────────────────────

    @PostMapping("/report")
    @Operation(
        summary = "[CITIZEN] Report an Emergency – Step 1",
        description = """
            POSTMAN: POST http://localhost:8080/api/emergencies/report
            Header: Authorization: Bearer <CITIZEN_TOKEN>
            Body:
            {
              "type": "ACCIDENT",
              "location": "Anna Nagar, Chennai - Near Metro Station",
              "description": "Road accident, 2 people injured"
            }
            Types: ACCIDENT | HEART_ATTACK | FIRE | STROKE | FALL | OTHER
            → After this, Dispatcher sees it in GET /emergencies/pending
            """
    )
    public ResponseEntity<ApiResponse<Emergency>> report(
            @Valid @RequestBody EmergencyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long citizenId = resolveUserId(userDetails);
        Emergency emergency = emergencyService.reportEmergency(citizenId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Emergency reported. Help is on the way.", emergency));
    }

    // ── STEP 2a: Dispatcher views all pending (REPORTED) emergencies ──────────

    @GetMapping("/pending")
    @Operation(
        summary = "[DISPATCHER] View All Pending Emergencies – Step 2a",
        description = """
            POSTMAN: GET http://localhost:8080/api/emergencies/pending
            Header: Authorization: Bearer <DISPATCHER_TOKEN>
            No body needed.
            → Returns all REPORTED emergencies (newest first).
            → Frontend polls this endpoint every 10–30s to show dispatcher alerts.
            → Note the 'emergencyId' from results – needed for dispatch step.
            """
    )
    public ResponseEntity<ApiResponse<List<Emergency>>> getPending() {
        return ResponseEntity.ok(
                ApiResponse.ok("Pending emergencies", emergencyService.getReportedEmergencies()));
    }

    // ── STEP 2b: Dispatcher checks which ambulances are free ──────────────────

    @GetMapping("/ambulances/available")
    @Operation(
        summary = "[DISPATCHER] Get Available Ambulances – Step 2b",
        description = """
            POSTMAN: GET http://localhost:8080/api/emergencies/ambulances/available
            Header: Authorization: Bearer <DISPATCHER_TOKEN>
            No body needed.
            → Returns all ambulances with status = AVAILABLE.
            → Pick an 'ambulanceId' from this list to use in the dispatch step.
            → If empty list: all ambulances are DISPATCHED or under MAINTENANCE.
            """
    )
    public ResponseEntity<ApiResponse<List<Ambulance>>> getAvailableAmbulances() {
        return ResponseEntity.ok(
                ApiResponse.ok("Available ambulances", emergencyService.getAvailableAmbulances()));
    }

    // ── STEP 2c: Dispatcher assigns ambulance to emergency ────────────────────

    @PostMapping("/{emergencyId}/dispatch")
    @Operation(
        summary = "[DISPATCHER] Assign Ambulance to Emergency – Step 2c",
        description = """
            POSTMAN: POST http://localhost:8080/api/emergencies/1/dispatch
            (Replace '1' with the actual emergencyId from /emergencies/pending)
            Header: Authorization: Bearer <DISPATCHER_TOKEN>
            Body:
            {
              "ambulanceId": 1
            }
            (Use ambulanceId from GET /emergencies/ambulances/available)
            
            WHAT HAPPENS:
            → ambulance.status = DISPATCHED (removed from available list)
            → emergency.status = DISPATCHED
            → Admin sees it in GET /emergencies/dispatched
            
            ERROR if ambulance not AVAILABLE:
            { "message": "Ambulance TN-01-AB-1234 is not available (current status: DISPATCHED)" }
            """
    )
    public ResponseEntity<ApiResponse<Emergency>> dispatch(
            @PathVariable Long emergencyId,
            @Valid @RequestBody DispatchRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long dispatcherId = resolveUserId(userDetails);
        Emergency emergency = emergencyService.dispatchAmbulance(emergencyId, dispatcherId, request);
        return ResponseEntity.ok(ApiResponse.ok("Ambulance dispatched successfully", emergency));
    }

    // ── ADMIN VIEW: See dispatched emergencies ready for patient admission ─────

    @GetMapping("/dispatched")
    @Operation(
        summary = "[ADMIN] View Dispatched Emergencies (Ready for Patient Admission)",
        description = """
            POSTMAN: GET http://localhost:8080/api/emergencies/dispatched
            Header: Authorization: Bearer <ADMIN_TOKEN>
            No body needed.
            → Returns all DISPATCHED emergencies.
            → Admin uses emergencyId from this list to call POST /patients/admit.
            → Frontend polls this every 10–30s to show admin admission alerts.
            """
    )
    public ResponseEntity<ApiResponse<List<Emergency>>> getDispatched() {
        return ResponseEntity.ok(
                ApiResponse.ok("Dispatched emergencies – ready for patient admission",
                        emergencyService.getDispatchedEmergencies()));
    }

    // ── Citizen views their own history ──────────────────────────────────────

    @GetMapping("/my")
    @Operation(
        summary = "[CITIZEN] View My Emergency History",
        description = """
            POSTMAN: GET http://localhost:8080/api/emergencies/my
            Header: Authorization: Bearer <CITIZEN_TOKEN>
            No body needed.
            → Returns all emergencies reported by the logged-in citizen.
            """
    )
    public ResponseEntity<ApiResponse<List<Emergency>>> getMyCases(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long citizenId = resolveUserId(userDetails);
        return ResponseEntity.ok(
                ApiResponse.ok("Your emergency history", emergencyService.getMyCases(citizenId)));
    }

    // ── Get one emergency by ID ───────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(
        summary = "[ANY] Get Emergency Detail by ID",
        description = """
            POSTMAN: GET http://localhost:8080/api/emergencies/1
            Header: Authorization: Bearer <ANY_VALID_TOKEN>
            No body needed.
            → Returns full detail of one emergency including citizen, dispatcher, ambulance info.
            """
    )
    public ResponseEntity<ApiResponse<Emergency>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.ok("Emergency details", emergencyService.getById(id)));
    }

    // ── Helper: resolve JWT email → userId ───────────────────────────────────

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found"))
                .getUserId();
    }
}
