package com.bng.drivo.ui.driver;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bng.drivo.R;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.model.Wallet;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.util.NavHeaderRating;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Pinta la cabecera del cajón del conductor (nav_header_driver.xml): identidad, saldo y viajes de
 * hoy. Es un ayudante y no un binding por pantalla porque esa cabecera aparece ahora en cuatro
 * sitios —Inicio y las tres secciones— y tiene que decir exactamente lo mismo en los cuatro: si
 * cada pantalla la rellenara por su cuenta, bastaría con tocar el formato en una para que el saldo
 * se leyera distinto según por dónde hubieras abierto el cajón.
 *
 * <p>Solo escribe en las vistas; quién pide los datos y cuándo los refresca es cosa de cada
 * pantalla. Inicio, por ejemplo, vuelve a llamar tras cada viaje cerrado.
 */
final class DriverNavHeader {

    private DriverNavHeader() {
    }

    /**
     * @param repository solo para el puente de la calificación: si {@code /me} ya trae el promedio
     *                   no se toca, y cuando el backend lo exponga este parámetro y
     *                   {@link DriverRatingLoader} desaparecen juntos.
     */
    static void applyProfile(@NonNull View header, @NonNull UserProfile profile,
                             @NonNull DriverRepository repository) {
        ((TextView) header.findViewById(R.id.text_nav_avatar)).setText(profile.getInitials());
        ((TextView) header.findViewById(R.id.text_nav_name)).setText(profile.getName());

        if (profile.getRating() != null) {
            NavHeaderRating.apply(header, profile.getRating(), profile.getTrips());
            return;
        }
        // Sin trips no se puede distinguir "conductor nuevo" de "promedio bajo", así que se pasa
        // null: mejor enseñar solo el número que arriesgarse a llamar "Nuevo" a quien no lo es.
        DriverRatingLoader.load(repository, rating -> NavHeaderRating.apply(header, rating, null));
    }

    static void applyWallet(@NonNull Context context, @NonNull View header, @NonNull Wallet wallet) {
        ((TextView) header.findViewById(R.id.text_nav_balance)).setText(String.format(Locale.getDefault(),
                context.getString(R.string.driver_home_wallet_balance_format), wallet.getBalance()));
        ((TextView) header.findViewById(R.id.text_nav_trips_today)).setText(
                context.getString(R.string.driver_home_trips_today_value, countTripsToday(wallet)));
    }

    /** Sin saldo no se escribe un cero: un cero es un saldo, y aquí lo que hay es un fallo. */
    static void applyWalletError(@NonNull Context context, @NonNull View header) {
        ((TextView) header.findViewById(R.id.text_nav_balance))
                .setText(R.string.driver_home_wallet_unavailable);
        ((TextView) header.findViewById(R.id.text_nav_trips_today))
                .setText(R.string.stat_empty);
    }

    /**
     * Los viajes de hoy se cuentan por las filas {@code commission} del wallet (una por viaje
     * cerrado), el mismo criterio que usa DriverEarningsActivity para que las dos pantallas no se
     * contradigan.
     */
    static int countTripsToday(@NonNull Wallet wallet) {
        LocalDate today = LocalDate.now();
        int trips = 0;
        for (Wallet.WalletEntry entry : wallet.getEntries()) {
            if (!"commission".equals(entry.getType())) {
                continue;
            }
            Long millis = parseInstantMillis(entry.getCreatedAt());
            if (millis != null && Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                    .toLocalDate().equals(today)) {
                trips++;
            }
        }
        return trips;
    }

    private static Long parseInstantMillis(String isoTimestamp) {
        if (isoTimestamp == null) {
            return null;
        }
        try {
            return Instant.parse(isoTimestamp).toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
