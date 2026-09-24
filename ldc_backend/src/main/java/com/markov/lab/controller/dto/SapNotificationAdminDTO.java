package com.markov.lab.controller.dto;

import com.markov.lab.entity.SapNotification;

import java.time.Instant;

/**
 * Vue Super Admin d'une {@code SapNotification} (Ticket #9). {@code SapNotification} masque ses
 * associations {@code emitter}/{@code equipment}/{@code intrant} au JSON via {@code @JsonIgnore}
 * (utilisées côté GraphQL, pas Jackson) : ce DTO les réexpose explicitement puisque le ticket exige
 * que la Structure émettrice, l'Équipement et l'Intrant soient visibles dans la liste.
 */
public record SapNotificationAdminDTO(
        long id,
        Instant createdAt,
        Long emitterId,
        String emitterName,
        Long equipmentId,
        String equipmentName,
        Long intrantId,
        String intrantName,
        Integer quantity,
        boolean isResolved,
        boolean isRejected
) {
    public static SapNotificationAdminDTO from(SapNotification notification) {
        return new SapNotificationAdminDTO(
                notification.getId(),
                notification.getCreatedAt(),
                notification.getEmitter() != null ? notification.getEmitter().getId() : null,
                notification.getEmitter() != null ? notification.getEmitter().getName() : null,
                notification.getEquipment() != null ? notification.getEquipment().getId() : null,
                notification.getEquipment() != null ? notification.getEquipment().getName() : null,
                notification.getIntrant() != null ? notification.getIntrant().getId() : null,
                notification.getIntrant() != null ? notification.getIntrant().getName() : null,
                notification.getQuantity(),
                notification.isResolved(),
                notification.isRejected()
        );
    }
}
