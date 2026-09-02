package com.bng.drivo.data.remote.dto;

/**
 * Una unidad disponible cerca, tal como la manda {@code GET /nearby-drivers}.
 *
 * <p>No trae identidad de ningún tipo —ni id, ni nombre, ni placa, ni calificación— y las
 * coordenadas vienen redondeadas a una celda de 150 m: es una foto aproximada de la zona, no
 * rastreo. Tampoco trae rumbo, por eso el sprite no puede orientarse con datos reales (ver
 * {@code NearbyDriversPresenter}).
 */
public class NearbyUnitDto {
    public double lat;
    public double lng;
    public Integer eta_min;
}
