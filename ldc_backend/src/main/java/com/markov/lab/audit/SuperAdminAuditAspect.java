package com.markov.lab.audit;

import com.markov.lab.entity.Account;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.User;
import com.markov.lab.repository.AccountRepository;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.PeriodRepository;
import com.markov.lab.repository.ReportRepository;
import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.StructureRepository;
import com.markov.lab.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Point d'interception UNIQUE du journal d'audit (Ticket #7) pour la console Super Admin.
 *
 * <p>Cet aspect {@code @Around} entoure toutes les méthodes publiques de
 * {@link com.markov.lab.controller.SuperAdminController}. Quand la requête HTTP sous-jacente est
 * une mutation (POST/PUT/PATCH/DELETE) sur une ressource connue (Compte, Utilisateur, Rapport,
 * Période, Rôle, Structure), une entrée {@link AuditLog} est écrite automatiquement après
 * exécution réussie du contrôleur — sans qu'aucune méthode du contrôleur n'ait besoin d'appeler
 * explicitement le journal d'audit. Aucune entrée n'est créée si le contrôleur lève une exception
 * (ex. 404, 400) : proceed() propage alors l'exception avant qu'on atteigne la persistance.</p>
 *
 * <h2>Comment l'entité et son id sont résolus génériquement</h2>
 * <ul>
 *   <li>Le {@code entityType} est déduit du premier segment de chemin après
 *       {@code /api/super-admin/} via {@link #SEGMENT_TO_ENTITY_TYPE} (ex. "periods" -&gt;
 *       "Period"). Une route qui ne suivrait pas cette convention REST (segment pluriel de la
 *       ressource) ou qui ne concerne pas une des 6 entités couvertes (ex. {@code /system/**},
 *       {@code /stats/**}) n'est pas auditée — c'est volontaire, ces routes ne font pas partie du
 *       périmètre du ticket.</li>
 *   <li>L'id est résolu depuis un {@code @PathVariable} nommé "id" ou "&lt;entité&gt;Id" (ex.
 *       "structureId" pour {@code DELETE /structures/{structureId}/equipments/{equipmentId}}),
 *       sinon depuis un champ "&lt;entité&gt;Id" du corps de la requête déjà désérialisé (ex.
 *       {@code ChangeRoleRequest.accountId()}).</li>
 * </ul>
 *
 * <h2>Limitation documentée : id d'une création (CREATE)</h2>
 * <p>Le seul endpoint de création actuellement dans le périmètre est {@code POST /periods}, dont
 * la réponse ne renvoie pas l'id créé (contrat d'API inchangé, comme demandé par le ticket). Cet
 * id est donc résolu au mieux-effort après exécution en relisant l'entité au plus grand id du
 * type concerné. C'est fiable pour un usage normal de console d'administration (faible volume,
 * un seul admin à la fois), mais pas garanti sous forte concurrence — un futur endpoint de
 * création qui exposerait l'id créé dans sa réponse permettrait de lever cette limitation.</p>
 *
 * <h2>Limitation documentée : snapshot des associations paresseuses</h2>
 * <p>Le snapshot avant/après recharge l'entité JPA par id et la sérialise via Jackson (voir
 * {@link AuditSnapshotService}). Les associations {@code @ManyToOne}/{@code @ManyToMany}
 * paresseuses non initialisées au moment de la sérialisation sont neutralisées par
 * {@code Hibernate6Module} (déjà configuré sur l'ObjectMapper applicatif) plutôt que déclenchées
 * : elles apparaissent alors à {@code null} dans le snapshot plutôt que de faire échouer
 * l'écriture du journal. Pour les 6 entités couvertes, c'est acceptable : les champs scalaires
 * pertinents (statut, dates, libellés...) sont eux toujours chargés avec l'entité.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminAuditAspect {

    private static final Map<String, String> SEGMENT_TO_ENTITY_TYPE = Map.of(
            "accounts", "Account",
            "users", "User",
            "reports", "Report",
            "periods", "Period",
            "roles", "Role",
            "structures", "Structure"
    );

    private final AuditLogRepository auditLogRepository;
    private final AuditSnapshotService snapshotService;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ReportRepository reportRepository;
    private final PeriodRepository periodRepository;
    private final RoleRepository roleRepository;
    private final StructureRepository structureRepository;

    private Map<String, JpaRepository<Object, Long>> repositoriesByType;

    @PostConstruct
    @SuppressWarnings("unchecked")
    void init() {
        repositoriesByType = new HashMap<>();
        repositoriesByType.put("Account", (JpaRepository<Object, Long>) (JpaRepository<?, ?>) accountRepository);
        repositoriesByType.put("User", (JpaRepository<Object, Long>) (JpaRepository<?, ?>) userRepository);
        repositoriesByType.put("Report", (JpaRepository<Object, Long>) (JpaRepository<?, ?>) reportRepository);
        repositoriesByType.put("Period", (JpaRepository<Object, Long>) (JpaRepository<?, ?>) periodRepository);
        repositoriesByType.put("Role", (JpaRepository<Object, Long>) (JpaRepository<?, ?>) roleRepository);
        repositoriesByType.put("Structure", (JpaRepository<Object, Long>) (JpaRepository<?, ?>) structureRepository);
    }

    @Around("target(com.markov.lab.controller.SuperAdminController)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        AuditAction action = request == null ? null : resolveAction(request.getMethod());
        if (action == null) {
            return pjp.proceed();
        }

        String entityType = resolveEntityType(request.getRequestURI());
        if (entityType == null) {
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Object[] args = pjp.getArgs();
        Long entityId = resolvePathId(entityType, signature.getMethod(), args);
        if (entityId == null) {
            entityId = resolveBodyId(entityType, args);
        }

        // IMPORTANT : le snapshot "before" est figé en JSON tout de suite, AVANT proceed().
        // En Open-Session-In-View, l'entité chargée ici et celle mutée par le contrôleur/service
        // partagent le même EntityManager (cache de premier niveau) : si on différait la
        // sérialisation après proceed(), on capturerait l'état déjà modifié au lieu de l'état
        // d'origine. Voir la javadoc de AuditSnapshotService#toSnapshotNode.
        JsonNode beforeSnapshot = (action != AuditAction.CREATE)
                ? snapshotService.toSnapshotNode(loadById(entityType, entityId))
                : null;

        Object result = pjp.proceed();

        Long resolvedId = entityId;
        if (resolvedId == null && action == AuditAction.CREATE) {
            resolvedId = bestEffortLatestId(entityType);
        }

        JsonNode afterSnapshot = (action != AuditAction.DELETE)
                ? snapshotService.toSnapshotNode(loadById(entityType, resolvedId))
                : null;

        try {
            AuditLog entry = new AuditLog();
            entry.setAccountId(currentAccountId());
            entry.setEntityType(entityType);
            entry.setEntityId(resolvedId);
            entry.setAction(action);
            entry.setTimestamp(Instant.now());
            entry.setSnapshot(snapshotService.combine(beforeSnapshot, afterSnapshot));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Le journal d'audit ne doit jamais faire échouer la requête métier qu'il observe.
            log.error("Echec de l'ecriture du journal d'audit pour {} {} id={}", entityType, action, resolvedId, e);
        }

        return result;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private AuditAction resolveAction(String httpMethod) {
        return switch (httpMethod) {
            case "POST" -> AuditAction.CREATE;
            case "PUT", "PATCH" -> AuditAction.UPDATE;
            case "DELETE" -> AuditAction.DELETE;
            default -> null;
        };
    }

    private String resolveEntityType(String uri) {
        String marker = "/api/super-admin/";
        int idx = uri.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String remainder = uri.substring(idx + marker.length());
        String firstSegment = remainder.split("/", 2)[0];
        return SEGMENT_TO_ENTITY_TYPE.get(firstSegment);
    }

    private Long resolvePathId(String entityType, Method method, Object[] args) {
        Parameter[] params = method.getParameters();
        String preferredName = Character.toLowerCase(entityType.charAt(0)) + entityType.substring(1) + "Id";
        Long fallback = null;
        for (int i = 0; i < params.length; i++) {
            PathVariable pv = params[i].getAnnotation(PathVariable.class);
            if (pv == null || !(args[i] instanceof Long value)) {
                continue;
            }
            String name = !pv.value().isBlank() ? pv.value() : params[i].getName();
            if ("id".equals(name)) {
                return value;
            }
            if (preferredName.equals(name)) {
                fallback = value;
            } else if (fallback == null) {
                fallback = value;
            }
        }
        return fallback;
    }

    private Long resolveBodyId(String entityType, Object[] args) {
        String fieldName = Character.toLowerCase(entityType.charAt(0)) + entityType.substring(1) + "Id";
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            Long value = extractLong(arg, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long extractLong(Object obj, String accessorName) {
        String getter = "get" + Character.toUpperCase(accessorName.charAt(0)) + accessorName.substring(1);
        for (String candidate : new String[]{accessorName, getter}) {
            try {
                Method m = obj.getClass().getMethod(candidate);
                Object value = m.invoke(obj);
                if (value instanceof Long l) {
                    return l;
                }
            } catch (Exception ignored) {
                // Méthode absente ou non applicable sur ce DTO : on essaie le candidat suivant.
            }
        }
        return null;
    }

    private Object loadById(String entityType, Long id) {
        if (id == null) {
            return null;
        }
        JpaRepository<Object, Long> repository = repositoriesByType.get(entityType);
        return repository == null ? null : repository.findById(id).orElse(null);
    }

    private Long bestEffortLatestId(String entityType) {
        JpaRepository<Object, Long> repository = repositoriesByType.get(entityType);
        if (repository == null) {
            return null;
        }
        return repository.findAll().stream()
                .map(this::extractId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null);
    }

    private Long extractId(Object entity) {
        try {
            Method m = entity.getClass().getMethod("getId");
            Object value = m.invoke(entity);
            if (value instanceof Long l) {
                return l;
            }
            if (value instanceof Number n) {
                return n.longValue();
            }
        } catch (Exception ignored) {
            // Pas d'accesseur getId() standard : cette entité ne peut pas être ordonnée, on l'ignore.
        }
        return null;
    }

    private Long currentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return null;
        }
        return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getAccount)
                .map(Account::getId)
                .orElse(null);
    }
}
