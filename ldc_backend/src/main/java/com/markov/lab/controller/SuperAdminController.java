package com.markov.lab.controller;

import com.markov.lab.controller.dto.*;
import com.markov.lab.entity.*;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.PeriodInput;
import com.markov.lab.input.RoleInput;
import com.markov.lab.repository.*;
import com.markov.lab.service.SuperAdminService;
import com.markov.lab.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin", produces = MediaType.APPLICATION_JSON_VALUE)
//@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final PeriodRepository periodRepository;
    private final RoleRepository roleRepository;
    private final StructureRepository structureRepository;
    private final MonthRepository monthRepository;
    private final EquipmentRepository equipmentRepository;
    private final TokenBlacklistService tokenBlacklistService;

    // ---- Stats ----

    @GetMapping("/stats")
    public ResponseEntity<AppStatsDTO> getStats() {
        return ResponseEntity.ok(superAdminService.getStats());
    }

    @GetMapping("/stats/filtered")
    public ResponseEntity<FilteredStatsDTO> getFilteredStats(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long structureId,
            @RequestParam(required = false) Long equipmentId) {
        return ResponseEntity.ok(superAdminService.getFilteredStats(periodId, structureId, equipmentId));
    }

    @GetMapping("/equipments")
    public ResponseEntity<List<Equipment>> getEquipments() {
        return ResponseEntity.ok(equipmentRepository.findAll());
    }

    // ---- Accounts ----

    @GetMapping("/accounts")
    public ResponseEntity<List<Account>> getAccounts() {
        return ResponseEntity.ok(accountRepository.findAllWithDetails());
    }

    @PatchMapping("/accounts/{id}/activate")
    public ResponseEntity<ApiSuccessResponse> activateAccount(@PathVariable Long id) {
        superAdminService.activateAccount(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Account activated"));
    }

    @PatchMapping("/accounts/{id}/deactivate")
    public ResponseEntity<ApiSuccessResponse> deactivateAccount(@PathVariable Long id) {
        superAdminService.deactivateAccount(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Account deactivated"));
    }

    @PatchMapping("/accounts/role")
    public ResponseEntity<ApiSuccessResponse> changeAccountRole(@RequestBody ChangeRoleRequest request) {
        superAdminService.changeAccountRole(request.accountId(), request.roleId());
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Account role updated"));
    }

    // ---- Users ----

    @PatchMapping("/users/reset-password")
    public ResponseEntity<ApiSuccessResponse> resetUserPassword(@RequestBody ResetPasswordRequest request) {
        superAdminService.resetUserPassword(request.userId(), request.newPassword());
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Password reset successfully"));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "User deleted"));
    }

    // ---- Reports ----

    @GetMapping("/reports")
    public ResponseEntity<List<Report>> getReports() {
        return ResponseEntity.ok(reportRepository.findAll());
    }

    @PatchMapping("/reports/status")
    public ResponseEntity<ApiSuccessResponse> changeReportStatus(@RequestBody ReportStatusRequest request) {
        superAdminService.changeReportStatus(request.reportId(), request.statusId());
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Report status updated"));
    }

    @PatchMapping("/reports/reassign")
    public ResponseEntity<ApiSuccessResponse> reassignReport(@RequestBody ReassignReportRequest request) {
        superAdminService.reassignReport(request.reportId(), request.accountId());
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Report reassigned"));
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteReport(@PathVariable Long id) {
        boolean hadDetails = superAdminService.deleteReport(id);
        String message = hadDetails ? "Report and details deleted" : "Report deleted (no details found)";
        return ResponseEntity.ok(new ApiSuccessResponse(200, message));
    }

    // ---- Periods ----

    @GetMapping("/periods")
    public ResponseEntity<List<Period>> getPeriods() {
        return ResponseEntity.ok(periodRepository.findAll());
    }

    @PostMapping("/periods")
    public ResponseEntity<ApiSuccessResponse> createPeriod(@RequestBody PeriodInput periodInput) {
        Period period = new Period();
        period.setPeriodName(periodInput.periodName());
        period.setStartDate(periodInput.startDate());
        period.setEndDate(periodInput.endDate());
        monthRepository.findByMonth(periodInput.monthName()).stream().findFirst()
                .ifPresent(period::setMonth);
        periodRepository.save(period);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Period created"));
    }

    @PutMapping("/periods/{id}")
    public ResponseEntity<ApiSuccessResponse> updatePeriod(@PathVariable Long id, @RequestBody PeriodInput periodInput) {
        Period period = periodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Period not found: " + id));
        period.setPeriodName(periodInput.periodName());
        period.setStartDate(periodInput.startDate());
        period.setEndDate(periodInput.endDate());
        monthRepository.findByMonth(periodInput.monthName()).stream().findFirst()
                .ifPresent(period::setMonth);
        periodRepository.save(period);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Period updated"));
    }

    @DeleteMapping("/periods/{id}")
    public ResponseEntity<ApiSuccessResponse> deletePeriod(@PathVariable Long id) {
        if (!periodRepository.existsById(id)) {
            throw new NotFoundException("Period not found: " + id);
        }
        periodRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Period deleted"));
    }

    // ---- Roles ----

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<ApiSuccessResponse> updateRole(@PathVariable Long id, @RequestBody RoleInput roleInput) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Role not found: " + id));
        role.setRole(roleInput.getRole());
        roleRepository.save(role);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Role updated"));
    }

    // ---- Structures ----

    @GetMapping("/structures")
    public ResponseEntity<List<Structure>> getStructures() {
        return ResponseEntity.ok(structureRepository.findAllWithDistrictAndRegion());
    }

    @DeleteMapping("/structures/{structureId}/equipments/{equipmentId}")
    public ResponseEntity<ApiSuccessResponse> removeEquipmentFromStructure(
            @PathVariable Long structureId, @PathVariable Long equipmentId) {
        superAdminService.removeEquipmentFromStructure(structureId, equipmentId);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Equipment removed from structure"));
    }

    @PatchMapping("/structures/{id}/toggle")
    public ResponseEntity<ApiSuccessResponse> toggleStructure(@PathVariable Long id) {
        Structure structure = structureRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Structure not found: " + id));
        Boolean current = structure.getActive();
        structure.setActive(current == null || !current);
        structureRepository.save(structure);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Structure active status toggled"));
    }

    // ---- System ----

    @GetMapping("/system/login-attempts")
    public ResponseEntity<Page<LoginAttempt>> getLoginAttempts(
            @RequestParam int page,
            @RequestParam int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(superAdminService.getLoginAttempts(pageable));
    }

    @GetMapping("/system/blacklist-stats")
    public ResponseEntity<Map<String, Long>> getBlacklistStats() {
        return ResponseEntity.ok(Map.of("blacklistedTokens", tokenBlacklistService.size()));
    }

    @DeleteMapping("/system/blacklist")
    public ResponseEntity<ApiSuccessResponse> purgeBlacklist() {
        superAdminService.purgeBlacklist();
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Token blacklist purged"));
    }
}
