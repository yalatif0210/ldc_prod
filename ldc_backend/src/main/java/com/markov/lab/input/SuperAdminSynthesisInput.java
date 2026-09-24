package com.markov.lab.input;

/**
 * Entrée dédiée à la console Super Admin (Ticket #15) pour {@code Synthesis} — à ne pas confondre
 * avec l'écran {@code synthesis-admin} déjà existant côté frontend, qui gère en réalité la
 * suppression de {@code Report} sous un nom trompeur.
 */
public record SuperAdminSynthesisInput(
        String item,
        Long synthesisTypeId,
        Long informationUnitId) {
}
