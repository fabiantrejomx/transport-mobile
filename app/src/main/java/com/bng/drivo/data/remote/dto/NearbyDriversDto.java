package com.bng.drivo.data.remote.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /nearby-drivers}: hasta tres unidades y el radio que representan.
 *
 * <p>{@code drivers} vacío es una respuesta normal, no un error — el servidor la devuelve tanto
 * cuando no hay nadie en el radio como cuando hay menos unidades que el mínimo que considera útil
 * mostrar.
 */
public class NearbyDriversDto {
    public List<NearbyUnitDto> drivers;
    public Integer radius_m;
}
