package com.markov.lab.service;

import com.markov.lab.controller.dto.AppStatsDTO;
import com.markov.lab.controller.dto.FilteredStatsDTO;
import com.markov.lab.repository.EquipmentRepository;
import java.util.Map;
import java.util.stream.Collectors;
import com.markov.lab.entity.*;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final PeriodRepository periodRepository;
    private final StructureRepository structureRepository;
    private final RoleRepository roleRepository;
    private final StatusRepository statusRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final MonthRepository monthRepository;
    private final EquipmentRepository equipmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final IntrantMvtDataRepository intrantMvtDataRepository;
    private final LabActivityDataRepository labActivityDataRepository;
    private final AdjustmentRepository adjustmentRepository;

    // ---- Stats ----

    public AppStatsDTO getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = accountRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                .count();
        long totalReports = reportRepository.count();
        long totalPeriods = periodRepository.count();
        long totalStructures = structureRepository.count();
        long totalRoles = roleRepository.count();

        return new AppStatsDTO(totalUsers, activeUsers, totalReports, totalPeriods, totalStructures, totalRoles);
    }

    @Transactional(readOnly = true)
    public FilteredStatsDTO getFilteredStats(Long periodId, Long structureId, Long equipmentId) {
        var reports = reportRepository.findFiltered(periodId, structureId, equipmentId);
        long reportCount = reports.size();
        Map<String, Long> reportsByStatus = reports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStatus() != null ? r.getStatus().getStatus() : "Non défini",
                        Collectors.counting()
                ));
        String periodLabel = periodId != null
                ? periodRepository.findById(periodId).map(Period::getPeriodName).orElse("Inconnu")
                : "Toutes";
        String structureLabel = structureId != null
                ? structureRepository.findById(structureId).map(Structure::getName).orElse("Inconnue")
                : "Toutes";
        String equipmentLabel = equipmentId != null
                ? equipmentRepository.findById(equipmentId).map(Equipment::getName).orElse("Inconnu")
                : "Tous";
        return new FilteredStatsDTO(reportCount, reportsByStatus, periodLabel, structureLabel, equipmentLabel);
    }

    // ---- Accounts ----

    @Transactional
    public void activateAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
        account.setIsActive(true);
        accountRepository.save(account);
        log.info("Account {} activated", id);
    }

    @Transactional
    public void deactivateAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
        account.setIsActive(false);
        accountRepository.save(account);
        log.info("Account {} deactivated", id);
    }

    @Transactional
    public void changeAccountRole(Long accountId, Long roleId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
        var role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        account.setRole(role);
        accountRepository.save(account);
        log.info("Account {} role changed to {}", accountId, roleId);
    }

    // ---- Users ----

    @Transactional
    public void resetUserPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password reset for user {}", userId);
    }

    // ---- Reports ----

    @Transactional
    public void changeReportStatus(Long reportId, Long statusId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found: " + reportId));
        var status = statusRepository.findById(statusId)
                .orElseThrow(() -> new NotFoundException("Status not found: " + statusId));
        report.setStatus(status);
        reportRepository.save(report);
        log.info("Report {} status changed to {}", reportId, statusId);
    }

    @Transactional
    public void reassignReport(Long reportId, Long accountId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found: " + reportId));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
        report.setAccount(account);
        reportRepository.save(report);
        log.info("Report {} reassigned to account {}", reportId, accountId);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
        log.info("User {} deleted by super admin", id);
    }

    @Transactional
    public boolean deleteReport(Long id) {
        if (!reportRepository.existsById(id)) {
            throw new NotFoundException("Report not found: " + id);
        }
        boolean hadDetails = intrantMvtDataRepository.existsByReport_Id(id)
                || labActivityDataRepository.existsByReport_Id(id);
        // 1. Adjustments (enfants de IntrantMvtData)
        adjustmentRepository.deleteByIntrantMvtData_Report_Id(id);
        // 2. IntrantMvtData
        intrantMvtDataRepository.deleteByReport_Id(id);
        // 3. LabActivityData
        labActivityDataRepository.deleteByReport_Id(id);
        // 4. Report
        reportRepository.deleteById(id);
        log.info("Report {} deleted by super admin", id);
        return hadDetails;
    }

    @Transactional
    public void removeEquipmentFromStructure(Long structureId, Long equipmentId) {
        Structure structure = structureRepository.findById(structureId)
                .orElseThrow(() -> new NotFoundException("Structure not found: " + structureId));
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new NotFoundException("Equipment not found: " + equipmentId));
        structure.getEquipments().remove(equipment);
        structureRepository.save(structure);
        log.info("Equipment {} removed from structure {}", equipmentId, structureId);
    }

    @Transactional
    public void updatePeriod(Long id, String periodName, java.time.LocalDate startDate, java.time.LocalDate endDate, String monthName) {
        Period period = periodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Period not found: " + id));
        period.setPeriodName(periodName);
        period.setStartDate(startDate);
        period.setEndDate(endDate);
        monthRepository.findByMonth(monthName).stream().findFirst()
                .ifPresent(period::setMonth);
        periodRepository.save(period);
        log.info("Period {} updated by super admin", id);
    }

    @Transactional
    public void deletePeriod(Long id) {
        if (!periodRepository.existsById(id)) {
            throw new NotFoundException("Period not found: " + id);
        }
        periodRepository.deleteById(id);
        log.info("Period {} deleted by super admin", id);
    }

    @Transactional
    public void updateRole(Long id, String roleName) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Role not found: " + id));
        role.setRole(roleName);
        roleRepository.save(role);
        log.info("Role {} updated to '{}' by super admin", id, roleName);
    }

    @Transactional
    public void toggleStructure(Long id) {
        Structure structure = structureRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Structure not found: " + id));
        Boolean current = structure.getActive();
        structure.setActive(current == null || !current);
        structureRepository.save(structure);
        log.info("Structure {} active status toggled by super admin", id);
    }

    // ---- System ----

    public void purgeBlacklist() {
        tokenBlacklistService.purgeAll();
        log.info("Token blacklist purged by super admin");
    }

    public Page<LoginAttempt> getLoginAttempts(Pageable pageable) {
        return loginAttemptRepository.findAll(pageable);
    }
}
