package com.markov.lab.input;

/**
 * Entrée dédiée à la console Super Admin (Ticket #15) pour {@code Information}.
 * Distincte de {@link InformationInput} (utilisée par la mutation GraphQL existante) pour ne pas
 * changer le contrat GraphQL en place : cette entrée expose en plus {@code isActive}, piloté
 * explicitement depuis la console.
 */
public record SuperAdminInformationInput(
        Long informationUnitId,
        Long informationSubUnitId,
        Long informationSubSubUnitId,
        Long equipmentId,
        Boolean isActive) {
}
