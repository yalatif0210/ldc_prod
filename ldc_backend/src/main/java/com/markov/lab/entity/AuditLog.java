package com.markov.lab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entrée du journal d'audit de la console Super Admin (Ticket #7).
 *
 * <p>Une entrée est écrite automatiquement par {@link com.markov.lab.audit.SuperAdminAuditAspect}
 * pour chaque mutation (POST/PUT/PATCH/DELETE) réussie sous {@code /api/super-admin/**}. Cette
 * entité est en lecture seule côté API : aucun endpoint de création/modification/suppression n'est
 * exposé pour {@link AuditLog}.</p>
 */
@Entity
@Table(name = "audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Id du {@link Account} auteur de la mutation (choisi plutôt qu'une référence directe ou un
     * username, pour rester stable même si le compte est ensuite renommé/désactivé/supprimé).
     * Peut être {@code null} si l'appelant authentifié n'a pas pu être résolu.
     */
    private Long accountId;

    /**
     * Type de l'entité métier concernée, ex. "Account", "User", "Report", "Period", "Role",
     * "Structure".
     */
    private String entityType;

    /**
     * Id de l'entité concernée. Peut être {@code null} si non résolvable génériquement (voir
     * limitations documentées dans {@link com.markov.lab.audit.SuperAdminAuditAspect}).
     */
    private Long entityId;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    private Instant timestamp;

    /**
     * Snapshot JSON {@code {"before": ..., "after": ...}} de l'état pertinent selon l'action :
     * seulement "after" pour CREATE, "before" et "after" pour UPDATE, seulement "before" pour
     * DELETE. Les champs sensibles (mot de passe, secret, token) sont retirés avant stockage.
     *
     * <p>Volontairement PAS annoté {@code @Lob} : avec PostgreSQL, {@code @Lob} sur un
     * {@code String} pousse Hibernate à traiter la colonne comme un flux LOB nécessitant une
     * session ouverte au moment de la lecture, ce qui échoue ("Unable to access lob stream") dès
     * que l'entité est lue puis sérialisée dans une réponse HTTP après la fin de la transaction.
     * {@code columnDefinition = "TEXT"} suffit pour stocker un JSON de taille arbitraire tout en
     * gardant une lecture/écriture de {@code String} classique.</p>
     */
    @Column(columnDefinition = "TEXT")
    private String snapshot;
}
