package com.markov.lab.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Construit le snapshot JSON {@code {"before": ..., "after": ...}} stocké dans
 * {@link com.markov.lab.entity.AuditLog#getSnapshot()}.
 *
 * <p>La sérialisation réutilise l'{@link ObjectMapper} applicatif (voir
 * {@code com.markov.lab.configuration.JacksonConfig}), qui a déjà le module
 * {@code Hibernate6Module} enregistré : les associations JPA paresseuses non initialisées sont
 * neutralisées (sérialisées à {@code null}) au lieu de déclencher une
 * {@code LazyInitializationException}, ce qui rend cette sérialisation "à plat" sûre même en
 * dehors d'une session Hibernate ouverte.</p>
 *
 * <p>Par sécurité, tout champ dont le nom contient "password", "secret" ou "token" (insensible à
 * la casse) est retiré récursivement avant stockage — certaines entités du domaine (ex.
 * {@code User.password}) n'ont pas de {@code @JsonIgnore} car elles sont aussi renvoyées telles
 * quelles par d'autres endpoints existants ; le journal d'audit ne doit pas dupliquer ni aggraver
 * cette exposition.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditSnapshotService {

    private static final Set<String> SENSITIVE_TOKENS = Set.of("password", "secret", "token");

    private final ObjectMapper objectMapper;

    /**
     * Convertit immédiatement une entité JPA en {@link JsonNode} indépendant (copie profonde,
     * champs sensibles retirés).
     *
     * <p>IMPORTANT : cette conversion doit être appelée tout de suite après chargement de
     * l'entité, jamais différée. En Open-Session-In-View (activé par défaut dans cette
     * application), une entité "before" chargée avant la mutation et une entité "after" rechargée
     * après peuvent être le MÊME objet Java géré par le même {@code EntityManager} (cache de
     * premier niveau) — si on attend la fin de la requête pour sérialiser "before", on obtient en
     * réalité l'état déjà muté. {@link #toSnapshotNode(Object)} fige donc l'état sous forme de
     * JSON immuable au moment de l'appel.</p>
     */
    public JsonNode toSnapshotNode(Object entity) {
        if (entity == null) {
            return NullNode.getInstance();
        }
        JsonNode node = objectMapper.valueToTree(entity);
        stripSensitiveFields(node);
        return node;
    }

    /** Combine deux snapshots déjà figés (voir {@link #toSnapshotNode(Object)}) en un seul JSON. */
    public String combine(JsonNode before, JsonNode after) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("before", before == null ? NullNode.getInstance() : before);
        root.set("after", after == null ? NullNode.getInstance() : after);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("Echec de sérialisation du snapshot d'audit, snapshot vide enregistré", e);
            return "{}";
        }
    }

    private void stripSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> toRemove = new ArrayList<>();
            obj.fieldNames().forEachRemaining(name -> {
                String lower = name.toLowerCase(Locale.ROOT);
                if (SENSITIVE_TOKENS.stream().anyMatch(lower::contains)) {
                    toRemove.add(name);
                }
            });
            toRemove.forEach(obj::remove);
            obj.elements().forEachRemaining(this::stripSensitiveFields);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::stripSensitiveFields);
        }
    }
}
