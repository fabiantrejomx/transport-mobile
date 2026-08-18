package com.bng.drivo.ui.trip;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ruta "emulada" entre dos puntos: no sigue calles reales (no hay integración con Routes API
 * todavía, ver CLAUDE.md), pero desplaza un par de puntos intermedios para que no se vea una
 * línea perfectamente recta al animarla en el mapa. Suficiente para el mock actual — cuando se
 * integre backend/Routes API, esta clase se sustituye por la polyline real del servicio.
 */
final class SimulatedRoute {

    private static final Random RANDOM = new Random();
    private static final double EARTH_RADIUS_METERS = 6371000;

    private final List<LatLng> points;
    private final double[] cumulativeDistances;
    private final double totalDistanceMeters;

    private SimulatedRoute(List<LatLng> points) {
        this.points = points;
        this.cumulativeDistances = new double[points.size()];
        double sum = 0;
        for (int i = 1; i < points.size(); i++) {
            sum += distanceMeters(points.get(i - 1), points.get(i));
            cumulativeDistances[i] = sum;
        }
        this.totalDistanceMeters = sum;
    }

    static SimulatedRoute between(LatLng start, LatLng end) {
        List<LatLng> path = new ArrayList<>();
        path.add(start);

        double bearing = bearingBetween(start, end);
        double distance = distanceMeters(start, end);
        double perpendicularBearing = bearing + 90;

        int waypointCount = distance > 3000 ? 3 : 2;
        for (int i = 1; i <= waypointCount; i++) {
            double fraction = (double) i / (waypointCount + 1);
            LatLng midpoint = lerp(start, end, fraction);
            double jitterMeters = (RANDOM.nextDouble() - 0.5) * distance * 0.15;
            double jitterBearing = jitterMeters >= 0 ? perpendicularBearing : perpendicularBearing + 180;
            path.add(offset(midpoint, Math.abs(jitterMeters), jitterBearing));
        }
        path.add(end);
        return new SimulatedRoute(path);
    }

    /** Punto de partida plausible para el conductor: unos cuantos cientos de metros del origen. */
    static LatLng spawnNearbyDriverStart(LatLng passengerLocation) {
        double distanceMeters = 900 + RANDOM.nextDouble() * 1300; // 900m - 2200m
        double bearingDegrees = RANDOM.nextDouble() * 360;
        return offset(passengerLocation, distanceMeters, bearingDegrees);
    }

    List<LatLng> getPoints() {
        return points;
    }

    double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    /** Posición interpolada a lo largo de la ruta para una fracción de avance [0, 1]. */
    LatLng pointAt(float fraction) {
        if (points.size() == 1 || totalDistanceMeters == 0) {
            return points.get(points.size() - 1);
        }
        double targetDistance = totalDistanceMeters * Math.max(0f, Math.min(1f, fraction));
        for (int i = 1; i < points.size(); i++) {
            if (targetDistance <= cumulativeDistances[i] || i == points.size() - 1) {
                double segmentStart = cumulativeDistances[i - 1];
                double segmentLength = cumulativeDistances[i] - segmentStart;
                double segmentFraction = segmentLength == 0 ? 0 : (targetDistance - segmentStart) / segmentLength;
                return lerp(points.get(i - 1), points.get(i), segmentFraction);
            }
        }
        return points.get(points.size() - 1);
    }

    private static LatLng lerp(LatLng a, LatLng b, double fraction) {
        return new LatLng(
                a.latitude + (b.latitude - a.latitude) * fraction,
                a.longitude + (b.longitude - a.longitude) * fraction);
    }

    private static LatLng offset(LatLng origin, double distanceMeters, double bearingDegrees) {
        double bearing = Math.toRadians(bearingDegrees);
        double lat1 = Math.toRadians(origin.latitude);
        double lng1 = Math.toRadians(origin.longitude);
        double angularDistance = distanceMeters / EARTH_RADIUS_METERS;

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angularDistance)
                + Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing));
        double lng2 = lng1 + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
                Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2));

        return new LatLng(Math.toDegrees(lat2), Math.toDegrees(lng2));
    }

    static double bearingBetween(LatLng a, LatLng b) {
        double lat1 = Math.toRadians(a.latitude);
        double lat2 = Math.toRadians(b.latitude);
        double dLng = Math.toRadians(b.longitude - a.longitude);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private static double distanceMeters(LatLng a, LatLng b) {
        float[] results = new float[1];
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results);
        return results[0];
    }
}
