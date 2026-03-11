package com.citycare.controller;

import com.citycare.dto.request.LoginRequest;
import com.citycare.dto.request.RegisterRequest;
import com.citycare.dto.response.ApiResponse;
import com.citycare.dto.response.AuthResponse;
import com.citycare.service.impl.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ============================================================
 * AuthController.java  –  Public Authentication Endpoints
 * ============================================================
 *
 * No JWT token needed for any endpoint in this controller.
 * Configured as permitAll() in SecurityConfig.
 *
 * ============================================================
 * POSTMAN TESTING GUIDE
 * ============================================================
 *
 * BASE URL: http://localhost:8080/api
 *
 * ──────────────────────────────────────────────────────────────
 * [1] POST /auth/register  →  Citizen Self-Registration
 * ──────────────────────────────────────────────────────────────
 * METHOD : POST
 * URL    : http://localhost:8080/api/auth/register
 * HEADERS: Content-Type: application/json
 * BODY (raw JSON):
 * {
 *   "name": "Ravi Kumar",
 *   "email": "ravi@gmail.com",
 *   "password": "ravi1234",
 *   "phone": "9876543210"
 * }
 *
 * EXPECTED RESPONSE (201 Created):
 * {
 *   "success": true,
 *   "message": "Registration successful",
 *   "data": {
 *     "token": "eyJhbGciOiJIUzI1NiJ9...",
 *     "userId": 1,
 *     "name": "Ravi Kumar",
 *     "email": "ravi@gmail.com",
 *     "role": "CITIZEN"
 *   }
 * }
 *
 * SAVE the "token" value – you'll need it for all citizen endpoints.
 *
 * ERROR CASES:
 *   → 400 if email already registered: { "success": false, "message": "Email already registered: ravi@gmail.com" }
 *   → 400 if validation fails: { "success": false, "message": "Validation failed", "data": { "email": "Provide a valid email address" } }
 *
 * ──────────────────────────────────────────────────────────────
 * [2] POST /auth/login  →  Login for ALL Roles
 * ──────────────────────────────────────────────────────────────
 * METHOD : POST
 * URL    : http://localhost:8080/api/auth/login
 * HEADERS: Content-Type: application/json
 * BODY (raw JSON):
 * {
 *   "email": "ravi@gmail.com",
 *   "password": "ravi1234"
 * }
 *
 * SAME ENDPOINT works for ALL roles. Just change email/password:
 *   Citizen    → { "email": "ravi@gmail.com",       "password": "ravi1234" }
 *   Admin      → { "email": "admin@citycare.com",    "password": "admin123" }
 *   Doctor     → { "email": "dr.priya@citycare.com", "password": "doc1234" }
 *   Nurse      → { "email": "nurse.anbu@citycare.com","password": "nurse123" }
 *   Dispatcher → { "email": "disp1@citycare.com",    "password": "disp123" }
 *
 * EXPECTED RESPONSE (200 OK):
 * {
 *   "success": true,
 *   "message": "Login successful",
 *   "data": {
 *     "token": "eyJhbGciOiJIUzI1NiJ9...",
 *     "userId": 2,
 *     "name": "Admin User",
 *     "email": "admin@citycare.com",
 *     "role": "ADMIN"
 *   }
 * }
 *
 * HOW TO USE TOKEN IN POSTMAN (do this once after login):
 *   Option A – Per Request:
 *     Go to Headers tab → Add:
 *       Key:   Authorization
 *       Value: Bearer eyJhbGciOiJIUzI1NiJ9...
 *
 *   Option B – Collection-level (recommended):
 *     1. Click your Collection → Edit → Authorization tab
 *     2. Type: Bearer Token
 *     3. Paste the token → Save
 *     4. All requests in the collection inherit this token
 *
 * ============================================================
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "Register (Citizens only) and Login (all roles). No token needed.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
        summary = "Citizen Self-Registration",
        description = """
            Only CITIZEN role can use this endpoint.
            Staff (Doctor/Nurse), Dispatchers, and Admins are created by Admin via /admin/* endpoints.
            
            POSTMAN → POST http://localhost:8080/api/auth/register
            Body (raw JSON):
            {
              "name": "Ravi Kumar",
              "email": "ravi@gmail.com",
              "password": "ravi1234",
              "phone": "9876543210"
            }
            """
    )
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful", response));
    }

    @PostMapping("/login")
    @Operation(
        summary = "Login for ALL Roles",
        description = """
            Works for Citizen, Doctor, Nurse, Dispatcher, Admin.
            Returns JWT token + role. Frontend uses 'role' to redirect to correct dashboard.
            
            POSTMAN → POST http://localhost:8080/api/auth/login
            Body (raw JSON):
            {
              "email": "ravi@gmail.com",
              "password": "ravi1234"
            }
            
            After login: Copy the 'token' from response.
            Add to all future requests → Headers → Authorization: Bearer <token>
            """
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }
}
