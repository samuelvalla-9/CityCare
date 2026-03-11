package com.citycare.service.impl;

import com.citycare.dto.request.AdmitPatientRequest;
import com.citycare.entity.Ambulance;
import com.citycare.entity.Emergency;
import com.citycare.entity.Patient;
import com.citycare.entity.User;
import com.citycare.exception.BadRequestException;
import com.citycare.exception.ResourceNotFoundException;
import com.citycare.repository.AmbulanceRepository;
import com.citycare.repository.EmergencyRepository;
import com.citycare.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * ============================================================
 * PatientService.java  –  Patient Admission + Status Updates
 * ============================================================
 *
 * admitPatient() – STEP 3 of the main workflow (called by ADMIN):
 *
 *   1. Load the Emergency (must be in DISPATCHED status)
 *   2. Check no patient record exists yet for this emergency
 *   3. Create Patient linked to citizen + emergency
 *   4. Set emergency.status = ADMITTED
 *   5. ★ AUTO-RELEASE AMBULANCE: ambulance.status = AVAILABLE ★
 *
 *   All 5 steps run in ONE @Transactional block.
 *   If any step fails → ALL changes roll back → DB stays consistent.
 *
 * WHY AUTO-RELEASE AMBULANCE?
 *   Once the patient is admitted to hospital, the ambulance has
 *   returned to base and is free for the next emergency.
 *   This prevents dispatchers from seeing a permanently "busy" ambulance.
 *
 * updateStatus() – called by DOCTOR or NURSE:
 *   Sets patient status (UNDER_OBSERVATION, STABLE, DISCHARGED).
 *   On DISCHARGED:
 *     - dischargeDate is set
 *     - The linked emergency is CLOSED (case complete)
 *
 * ============================================================
 */
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final EmergencyRepository emergencyRepository;
    private final AmbulanceRepository ambulanceRepository;

    @Transactional
    public Patient admitPatient(AdmitPatientRequest req) {
        Emergency emergency = emergencyRepository.findById(req.getEmergencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Emergency", req.getEmergencyId()));

        // Must be DISPATCHED – can't admit from REPORTED (ambulance not assigned yet)
        if (emergency.getStatus() != Emergency.Status.DISPATCHED) {
            throw new BadRequestException(
                    "Cannot admit patient. Emergency status is " + emergency.getStatus() +
                    ". It must be in DISPATCHED status (ambulance assigned by dispatcher first).");
        }

        // Prevent duplicate admission for the same emergency
        patientRepository.findByEmergencyEmergencyId(req.getEmergencyId()).ifPresent(p -> {
            throw new BadRequestException(
                    "Patient already admitted for this emergency. Patient ID: " + p.getPatientId());
        });

        User citizen = emergency.getCitizen();

        // Create the patient record
        Patient patient = Patient.builder()
                .citizen(citizen)
                .emergency(emergency)
                .admissionDate(LocalDate.now())
                .ward(req.getWard())
                .notes(req.getNotes())
                .build(); // status defaults to ADMITTED

        patientRepository.save(patient);
        // Hibernate: INSERT INTO patients (citizen_id, emergency_id, admission_date, ...)

        // Update emergency to ADMITTED
        emergency.setStatus(Emergency.Status.ADMITTED);
        emergencyRepository.save(emergency);

        // ★ AUTO-RELEASE THE AMBULANCE BACK TO AVAILABLE ★
        // The ambulance has reached the hospital and is now free for the next call
        Ambulance ambulance = emergency.getAmbulance();
        if (ambulance != null) {
            ambulance.setStatus(Ambulance.Status.AVAILABLE);
            ambulanceRepository.save(ambulance);
            // Hibernate: UPDATE ambulances SET status='AVAILABLE' WHERE ambulance_id=?
        }

        return patient;
    }

    @Transactional
    public Patient updateStatus(Long patientId, Patient.Status newStatus) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        patient.setStatus(newStatus);

        if (newStatus == Patient.Status.DISCHARGED) {
            patient.setDischargeDate(LocalDate.now());
            // Close the emergency case when patient is discharged
            if (patient.getEmergency() != null) {
                patient.getEmergency().setStatus(Emergency.Status.CLOSED);
                emergencyRepository.save(patient.getEmergency());
            }
        }

        return patientRepository.save(patient);
    }

    public Patient getById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    public List<Patient> getAll() {
        return patientRepository.findAll();
    }

    public List<Patient> getByStatus(Patient.Status status) {
        return patientRepository.findByStatus(status);
    }

    public List<Patient> getByCitizen(Long citizenId) {
        return patientRepository.findByCitizenUserId(citizenId);
    }
}
