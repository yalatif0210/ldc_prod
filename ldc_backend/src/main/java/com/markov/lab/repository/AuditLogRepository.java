package com.markov.lab.repository;

import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Lecture seule : {@link AuditLog} n'expose volontairement aucune méthode de sauvegarde
 * personnalisée ni d'endpoint REST de modification/suppression (voir SuperAdminController).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Note : entityType/action/accountId utilisent le motif "(:param IS NULL OR col = :param)".
    // Pour from/to (Instant -> timestamp), PostgreSQL échoue avec "could not determine data type
    // of parameter" (42P18) dès que ":from"/":to" apparaît dans une branche "IS NULL" sans
    // contexte typé, y compris en réordonnant les clauses : chaque occurrence textuelle du
    // paramètre semble décrite comme un bind JDBC indépendant. On évite donc tout "IS NULL" sur
    // ces deux paramètres : SuperAdminController fournit toujours des bornes effectives
    // (Instant.EPOCH / une date future lointaine par défaut quand l'appelant ne filtre pas).
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:entityType IS NULL OR a.entityType = :entityType)
              AND (:action IS NULL OR a.action = :action)
              AND (:accountId IS NULL OR a.accountId = :accountId)
              AND a.timestamp >= :from
              AND a.timestamp <= :to
            """)
    Page<AuditLog> search(@Param("entityType") String entityType,
                           @Param("action") AuditAction action,
                           @Param("accountId") Long accountId,
                           @Param("from") Instant from,
                           @Param("to") Instant to,
                           Pageable pageable);
}
