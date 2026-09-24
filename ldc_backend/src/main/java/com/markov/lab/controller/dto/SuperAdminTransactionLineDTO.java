package com.markov.lab.controller.dto;

import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.SanguineProduct;

/**
 * Représentation d'une ligne fille d'un {@code Transaction} (Ticket #8) pour la console
 * Super Admin. Volontairement distincte des entités {@code SanguineProductTransaction}/
 * {@code MedicinesTransaction} : ces deux entités portent une référence {@code ManyToOne}
 * vers leur {@code Transaction} parente qui, si elle était sérialisée telle quelle à
 * l'intérieur d'un {@link SuperAdminTransactionDTO}, provoquerait une boucle infinie
 * (Transaction -&gt; lignes -&gt; Transaction -&gt; ...). Ce DTO n'expose donc que le produit
 * sanguin OU l'intrant concerné, jamais la transaction parente.
 */
public record SuperAdminTransactionLineDTO(
        Long id,
        SanguineProduct sanguineProduct,
        Intrant intrant,
        Integer quantity
) {
    public static SuperAdminTransactionLineDTO ofSanguineProduct(Long id, SanguineProduct sanguineProduct, Integer quantity) {
        return new SuperAdminTransactionLineDTO(id, sanguineProduct, null, quantity);
    }

    public static SuperAdminTransactionLineDTO ofIntrant(Long id, Intrant intrant, Integer quantity) {
        return new SuperAdminTransactionLineDTO(id, null, intrant, quantity);
    }
}
