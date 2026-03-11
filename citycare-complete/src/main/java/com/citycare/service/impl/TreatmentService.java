package com.citycare.service.impl;

import com.citycare.dto.request.TreatmentRequest;
import com.citycare.entity.Patient;
import com.citycare.entity.Treatment;
import com.citycare.entity.User;
import com.citycare.exception.BadRequestException;
import com.citycare.exception.ResourceNotFoundException;
import com.citycare.repository.PatientRepository;
import com.citycare.repository.TreatmentRepository;
import com.citycare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * ============================================================
 * TreatmentService.java  –  Medical Treatment Management
 * ============================================================
 *
 * assignTreatment() – called by DOCTOR or NURSE:
 *   1. Load patient – must exist and NOT be DISCHARGED
 *   2. Load staff member (doctor/nurse) from DB
 *   3. Double-check their role is DOCTOR or NURSE
 *   4. Create Treatment record linked to patient + staff
 *   5. Status starts as ONGOING
 *
 * updateStatus() – called by DOCTOR or NURSE:
 *   Changes treatment from ONGOING to COMPLETED or CANCELLED.
 *   Records the endDate automatically.
 *
 * ============================================================
 * HOW THIS FILE WORKS:
 *   Called by TreatmentController.
 *   The staffId is taken from the JWT (logged-in user) – not from request body.
 *   This prevents one doctor from "assigning" a treatment as another doctor.
 * ============================================================
 */
@Service
@RequiredArgsConstructor
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    @Transactional
    public Treatment assignTreatment(Long staffId, TreatmentRequest req) {
        Patient patient = patientRepository.findById(req.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", req.getPatientId()));

        if (patient.getStatus() == Patient.Status.DISCHARGED) {
            throw new BadRequestException(
                    "Cannot assign treatment to patient " + req.getPatientId() +
                    " – they have already been discharged.");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", staffId));

        // Belt-and-suspenders check (SecurityConfig already enforces this via roles)
        if (staff.getRole() != User.Role.DOCTOR && staff.getRole() != User.Role.NURSE) {
            throw new BadRequestException("Only DOCTOR or NURSE can assign treatments");
        }

        Treatment treatment = Treatment.builder()
                .patient(patient)
                .assignedBy(staff)
                .description(req.getDescription())
                .medicationName(req.getMedicationName())
                .dosage(req.getDosage())
                .build(); // status defaults to ONGOING, startDate defaults to today

        return treatmentRepository.save(treatment);
        // Hibernate: INSERT INTO treatments (patient_id, assigned_by, description, ...)
    }

    @Transactional
    public Treatment updateStatus(Long treatmentId, Treatment.Status newStatus) {
        Treatment treatment = treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment", treatmentId));

        treatment.setStatus(newStatus);

        // Record when the treatment ended
        if (newStatus == Treatment.Status.COMPLETED || newStatus == Treatment.Status.CANCELLED) {
            treatment.setEndDate(LocalDate.now());
        }

        return treatmentRepository.save(treatment);
    }

    public List<Treatment> getForPatient(Long patientId) {
        return treatmentRepository.findByPatientPatientId(patientId);
    }

    public List<Treatment> getMyAssigned(Long staffId) {
        return treatmentRepository.findByAssignedByUserId(staffId);
    }

    public Treatment getById(Long id) {
        return treatmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment", id));
    }
}
