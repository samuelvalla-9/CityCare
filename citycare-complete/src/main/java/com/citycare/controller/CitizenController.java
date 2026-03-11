package com.citycare.controller;

import com.citycare.dto.request.CitizenProfileRequest;
import com.citycare.dto.response.ApiResponse;
import com.citycare.entity.Citizen;
import com.citycare.entity.CitizenDocument;
import com.citycare.exception.ResourceNotFoundException;
import com.citycare.repository.UserRepository;
import com.citycare.service.impl.CitizenService;
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

@RestController
@RequestMapping("/citizens")
@RequiredArgsConstructor
@Tag(name = "6. Citizens", description = "Citizen profile and document management")
public class CitizenController {

    private final CitizenService citizenService;
    private final UserRepository userRepository;

    @PostMapping("/profile")
    @Operation(summary = "[CITIZEN] Create or Update Citizen Profile")
    public ResponseEntity<ApiResponse<Citizen>> upsertProfile(
            @Valid @RequestBody CitizenProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        Citizen citizen = citizenService.createOrUpdateProfile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Profile saved", citizen));
    }

    @GetMapping("/profile")
    @Operation(summary = "[CITIZEN] Get My Profile")
    public ResponseEntity<ApiResponse<Citizen>> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.ok("Citizen profile", citizenService.getProfile(userId)));
    }

    @GetMapping
    @Operation(summary = "[ADMIN] Get All Citizens")
    public ResponseEntity<ApiResponse<List<Citizen>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("All citizens", citizenService.getAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "[ANY] Get Citizen by ID")
    public ResponseEntity<ApiResponse<Citizen>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Citizen details", citizenService.getById(id)));
    }

    @PostMapping("/{id}/documents")
    @Operation(summary = "[CITIZEN/ADMIN] Upload Document")
    public ResponseEntity<ApiResponse<CitizenDocument>> uploadDocument(
            @PathVariable Long id,
            @RequestParam CitizenDocument.DocType docType,
            @RequestParam String fileUri) {
        CitizenDocument doc = citizenService.uploadDocument(id, docType, fileUri);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Document uploaded", doc));
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "[ANY] Get Documents for Citizen")
    public ResponseEntity<ApiResponse<List<CitizenDocument>>> getDocuments(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Documents", citizenService.getDocuments(id)));
    }

    @PatchMapping("/documents/{docId}/verify")
    @Operation(summary = "[ADMIN] Verify or Reject Document")
    public ResponseEntity<ApiResponse<CitizenDocument>> verifyDocument(
            @PathVariable Long docId,
            @RequestParam CitizenDocument.VerificationStatus status) {
        CitizenDocument doc = citizenService.verifyDocument(docId, status);
        return ResponseEntity.ok(ApiResponse.ok("Document status updated to " + status, doc));
    }

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found"))
                .getUserId();
    }
}
