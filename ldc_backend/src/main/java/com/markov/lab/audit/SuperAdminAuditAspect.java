package com.markov.lab.audit;

import com.markov.lab.entity.Account;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.User;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Point d'interception UNIQUE du journal d'audit (Ticket #7) pour la console Super Admin.
 *
 * <p>Cet aspect {@code @Around} entoure toutes les méthodes publiques de tout contrôleur
 * {@code com.markov.lab.controller.SuperAdmin*Controller}. Quand la requête HTTP sous-jacente est
 * une mutation (POST/PUT/PATCH/DELETE) sur une entité reconnue, une entrée {@link AuditLog} est
 * écrite automatiquement après exécution réussie du contrôleur — sans qu'aucune méthode du
 * contrôleur n'ait besoin d'appeler explicitement le journal d'audit. Aucune entrée n'est créée si
 * le contrôleur lève une exception (ex. 404, 400) : proceed() propage alors l'exception avant
 * qu'on atteigne la persistance.</p>
 *
 * <h2>Extensible sans toucher ce fichier — lis ceci avant d'ajouter une nouvelle entité</h2>
 * <p>Cet aspect intercepte désormais TOUT contrôleur dont le nom de classe suit le motif
 * {@code SuperAdmin<Entité>Controller} (ex. {@code SuperAdminTransactionController} -&gt;
 * entityType {@code "Transaction"}), à condition qu'un bean Spring Data nommé
 * {@code <entité en minuscule>Repository} existe (ex. {@code transactionRepository}, auto-généré
 * par Spring pour toute interface {@code TransactionRepository extends JpaRepository<...>} — rien
 * à enregistrer manuellement). <strong>Pour qu'une nouvelle entité soit auditée automatiquement,
 * il suffit de nommer son contrôleur dédié selon cette convention</strong> — aucune modification de
 * cette classe n'est nécessaire, ce qui évite que plusieurs tickets travaillant en parallèle sur
 * des entités différentes se marchent sur les mêmes lignes.</p>
 *
 * <p>Le contrôleur historique {@link com.markov.lab.controller.SuperAdminController} (Compte,
 * Utilisateur, Rapport, Période, Rôle, Structure dans une seule classe) ne suit pas cette
 * convention un-contrôleur-par-entité : il reste résolu via {@link #LEGACY_SEGMENT_TO_ENTITY_TYPE},
 * un repli explicite qui ne doit pas être étendu — toute nouvelle entité doit avoir son propre
 * contrôleur {@code SuperAdmin<Entité>Controller}.</p>
 *
 * <h2>Comment l'id est résolu génériquement</h2>
 * <p>Depuis un {@code @PathVariable} nommé "id" ou "&lt;entité&gt;Id" (ex. "structureId" pour
 * {@code DELETE /structures/{structureId}/equipments/{equipmentId}}), sinon depuis un champ
 * "&lt;entité&gt;Id" du corps de la requête déjà désérialisé (ex.
 * {@code ChangeRoleRequest.accountId()}).</p>
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

    /** Repli explicite pour le seul contrôleur historique qui gère plusieurs entités — voir Javadoc de classe. */
    private static final Map<String, String> LEGACY_SEGMENT_TO_ENTITY_TYPE = Map.of(
            "accounts", "Account",
            "users", "User",
            "reports", "Report",
            "periods", "Period",
            "roles", "Role",
            "structures", "Structure"
    );

    private static final Pattern CONTROLLER_NAME_PATTERN = Pattern.compile("^SuperAdmin(.+)Controller$");

    private final AuditLogRepository auditLogRepository;
    private final AuditSnapshotService snapshotService;
    private final UserRepository userRepository;
    private final ApplicationContext applicationContext;

    @Around("execution(* com.markov.lab.controller.SuperAdmin*Controller.*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        AuditAction action = request == null ? null : resolveAction(request.getMethod());
        if (action == null) {
            return pjp.proceed();
        }

        String entityType = resolveEntityType(pjp.getTarget(), request.getRequestURI());
        if (entityType == null || resolveRepository(entityType) == null) {
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
        //
        // Cette étape est protégée par son propre try/catch : le journal d'audit ne doit jamais
        // faire échouer la requête métier qu'il observe (ni l'empêcher de démarrer, ni la faire
        // paraître en échec après coup) — voir aussi le try/catch après proceed() ci-dessous.
        JsonNode beforeSnapshot = null;
        if (action != AuditAction.CREATE) {
            try {
                beforeSnapshot = snapshotService.toSnapshotNode(loadById(entityType, entityId));
            } catch (Exception e) {
                log.error("Echec du snapshot 'before' du journal d'audit pour {} {} id={}", entityType, action, entityId, e);
            }
        }

        Object result = pjp.proceed();

        try {
            Long resolvedId = entityId;
            if (resolvedId == null && action == AuditAction.CREATE) {
                resolvedId = bestEffortLatestId(entityType);
            }

            JsonNode afterSnapshot = (action != AuditAction.DELETE)
                    ? snapshotService.toSnapshotNode(loadById(entityType, resolvedId))
                    : null;

            AuditLog entry = new AuditLog();
            entry.setAccountId(currentAccountId());
            entry.setEntityType(entityType);
            entry.setEntityId(resolvedId);
            entry.setAction(action);
            entry.setTimestamp(Instant.now());
            entry.setSnapshot(snapshotService.combine(beforeSnapshot, afterSnapshot));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // La mutation métier (proceed() ci-dessus) a déjà réussi à ce stade : on ne doit
            // surtout pas propager cette exception, sous peine de faire paraître en échec une
            // action qui a en réalité réussi.
            log.error("Echec de l'ecriture du journal d'audit pour {} {} id={}", entityType, action, entityId, e);
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

    private String resolveEntityType(Object controllerTarget, String uri) {
        String controllerName = controllerTarget.getClass().getSimpleName();
        Matcher matcher = CONTROLLER_NAME_PATTERN.matcher(controllerName);
        if (matcher.matches() && !"".equals(matcher.group(1))
                && !com.markov.lab.controller.SuperAdminController.class.getSimpleName().equals(controllerName)) {
            return matcher.group(1);
        }
        return resolveLegacyEntityType(uri);
    }

    private String resolveLegacyEntityType(String uri) {
        String marker = "/api/super-admin/";
        int idx = uri.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String remainder = uri.substring(idx + marker.length());
        String[] segments = remainder.split("/");
        // "structures/{structureId}/equipments/{equipmentId}" est un endpoint de RELATION entre
        // deux entités, pas une action sur la Structure elle-même : la DELETE ne supprime pas la
        // Structure (elle détache un Équipement). La résoudre comme "Structure" DELETE produirait
        // une entrée trompeuse (voir Ticket #7, relevé en revue). Les autres routes à 3 segments
        // du contrôleur (ex. "accounts/{id}/activate", "structures/{id}/toggle") restent auditées
        // normalement : ce sont bien des mises à jour de l'entité de premier niveau.
        if (segments.length >= 3 && "equipments".equals(segments[2])) {
            return null;
        }
        return LEGACY_SEGMENT_TO_ENTITY_TYPE.get(segments[0]);
    }

    @SuppressWarnings("unchecked")
    private JpaRepository<Object, Long> resolveRepository(String entityType) {
        String beanName = Character.toLowerCase(entityType.charAt(0)) + entityType.substring(1) + "Repository";
        try {
            return (JpaRepository<Object, Long>) applicationContext.getBean(beanName, JpaRepository.class);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
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
        JpaRepository<Object, Long> repository = resolveRepository(entityType);
        return repository == null ? null : repository.findById(id).orElse(null);
    }

    private Long bestEffortLatestId(String entityType) {
        JpaRepository<Object, Long> repository = resolveRepository(entityType);
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
