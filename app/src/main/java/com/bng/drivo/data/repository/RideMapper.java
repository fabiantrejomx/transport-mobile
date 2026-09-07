package com.bng.drivo.data.repository;

import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.RideSummary;
import com.bng.drivo.data.model.Waypoint;
import com.bng.drivo.data.remote.dto.DriverSummaryDto;
import com.bng.drivo.data.remote.dto.LatLngDto;
import com.bng.drivo.data.remote.dto.PlaceDto;
import com.bng.drivo.data.remote.dto.RideDto;
import com.bng.drivo.data.remote.dto.RideSummaryDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * De los DTO del contrato a los modelos del viaje.
 *
 * <p>Vive aparte porque el mismo viaje lo leen los dos lados: {@code RestTripRepository} para el
 * pasajero y {@code RestDriverRepository} para el conductor. Cada uno tenía su copia del mapeo,
 * campo por campo e idénticas, y esa es justo la clase de duplicado que se rompe en silencio: un
 * campo nuevo que solo se conecta en una de las dos copias no falla al compilar, se queda en null
 * en la mitad de la app.
 */
final class RideMapper {

    private RideMapper() {}

    static Ride from(RideDto dto) {
        DriverSummaryDto driver = dto.driver;
        PlaceDto origin = dto.origin;
        PlaceDto destination = dto.destination;
        return new Ride(dto.id, dto.status, dto.agreed_fare,
                driver != null ? driver.name : null, driver != null ? driver.rating : null,
                driver != null ? driver.brand : null, driver != null ? driver.model : null,
                driver != null ? driver.color : null, driver != null ? driver.plate : null,
                origin != null ? origin.text : null, destination != null ? destination.text : null,
                origin != null ? origin.lat : null, origin != null ? origin.lng : null,
                destination != null ? destination.lat : null, destination != null ? destination.lng : null,
                dto.polyline, dto.requested_at, dto.driver_arrived_at, dto.commission,
                dto.passenger_offer, waypoints(dto.waypoints), dto.search_expires_at,
                Boolean.TRUE.equals(dto.ended_early));
    }

    static RideSummary from(RideSummaryDto dto) {
        return new RideSummary(dto.id, dto.status, dto.agreed_fare, dto.origin_text, dto.dest_text,
                dto.requested_at, dto.my_rating, Boolean.TRUE.equals(dto.ended_early));
    }

    /**
     * Las paradas llegan sin texto y aquí se quedan sin texto: {@link Waypoint} lo admite null y
     * dibujar la ruta solo necesita el punto. Poner la dirección es cosa de quien la enseñe.
     */
    private static List<Waypoint> waypoints(List<LatLngDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<Waypoint> stops = new ArrayList<>(dtos.size());
        for (LatLngDto dto : dtos) {
            if (dto != null) {
                stops.add(new Waypoint(dto.lat, dto.lng, null));
            }
        }
        return stops;
    }
}
