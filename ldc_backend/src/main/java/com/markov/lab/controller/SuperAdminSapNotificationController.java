package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.controller.dto.SapNotificationAdminDTO;
import com.markov.lab.entity.Equipment;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.SapNotification;
import com.markov.lab.entity.Structure;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.SapNotificationAdminInput;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.SapNotificationRepository;
import com.markov.lab.repository.StructureRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Console Super Admin — Notifications (Ticket #9, {@code SapNotification}).
 *
 * <p>Entité feuille (aucune ligne fille, pas de blocage de suppression nécessaire — voir Ticket
 * #5). Le nommage {@code SuperAdmin<Entité>Controller} active automatiquement le journal d'audit
 * générique ({@link com.markov.lab.audit.SuperAdminAuditAspect}, Ticket #7) : aucun appel explicite
 * au journal d'audit n'est nécessaire ici.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin - Notifications", description = "Administration des notifications SAP (SapNotification)")
@RequestMapping(path = "/api/super-admin/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminSapNotificationController {

    private final SapNotificationRepository sapNotificationRepository;
    private final StructureRepository structureRepository;
    private final EquipmentRepository equipmentRepository;
    private final IntrantRepository intrantRepository;

    @GetMapping
    public ResponseEntity<List<SapNotificationAdminDTO>> getNotifications() {
        List<SapNotificationAdminDTO> notifications = sapNotificationRepository.findAll().stream()
                .map(SapNotificationAdminDTO::from)
                .toList();
        return ResponseEntity.ok(notifications);
    }

    // Lookup pour les listes déroulantes du formulaire de création/modification. Les Structures et
    // Équipements ont déjà leur équivalent sur SuperAdminController (/structures, /equipments) ;
    // il n'existe pas encore d'endpoint REST Super Admin pour les Intrants (IntrantController n'est
    // que GraphQL), d'où ce lookup dédié, scopé aux notifications.
    @GetMapping("/intrants")
    public ResponseEntity<List<Intrant>> getIntrantsLookup() {
        return ResponseEntity.ok(intrantRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createNotification(@RequestBody SapNotificationAdminInput input) {
        SapNotification notification = new SapNotification();
        applyInput(notification, input);
        sapNotificationRepository.save(notification);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Notification created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateNotification(
            @PathVariable Long id, @RequestBody SapNotificationAdminInput input) {
        SapNotification notification = sapNotificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + id));
        applyInput(notification, input);
        sapNotificationRepository.save(notification);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Notification updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteNotification(@PathVariable Long id) {
        if (!sapNotificationRepository.existsById(id)) {
            throw new NotFoundException("Notification not found: " + id);
        }
        sapNotificationRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Notification deleted"));
    }

    private void applyInput(SapNotification notification, SapNotificationAdminInput input) {
        if (input.emitterId() != null) {
            Structure emitter = structureRepository.findById(input.emitterId())
                    .orElseThrow(() -> new NotFoundException("Structure not found: " + input.emitterId()));
            notification.setEmitter(emitter);
        }
        if (input.equipmentId() != null) {
            Equipment equipment = equipmentRepository.findById(input.equipmentId())
                    .orElseThrow(() -> new NotFoundException("Equipment not found: " + input.equipmentId()));
            notification.setEquipment(equipment);
        }
        if (input.intrantId() != null) {
            Intrant intrant = intrantRepository.findById(input.intrantId())
                    .orElseThrow(() -> new NotFoundException("Intrant not found: " + input.intrantId()));
            notification.setIntrant(intrant);
        }
        notification.setQuantity(input.quantity());
        notification.setResolved(input.isResolved());
        notification.setRejected(input.isRejected());
    }
}
