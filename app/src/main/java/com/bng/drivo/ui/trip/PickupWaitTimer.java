package com.bng.drivo.ui.trip;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.bng.drivo.R;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Los 5 minutos de cortesía que corren desde que el conductor marca "llegué al punto": el tiempo
 * que el pasajero tiene para salir al vehículo. Maneja el bloque de {@code view_pickup_wait.xml},
 * que las dos apps incluyen igual — el pasajero necesita saber cuánto le queda y el conductor
 * cuánto lleva esperando, y tienen que ver el mismo número.
 *
 * <p>El ancla es {@code driver_arrived_at}, la hora del servidor: la guarda el backend al validar
 * por GPS que el conductor está de verdad ahí (ver TripService.markArrived). Contar desde el reloj
 * del teléfono daría dos cuentas distintas, y reabrir la pantalla a mitad de la espera reiniciaría
 * el cronómetro. Si el dato no viene —una respuesta vieja del servidor—, se ancla en el momento en
 * que se pidió arrancar, que es lo más cercano que hay: es peor no mostrar nada.
 *
 * <p>Agotarse todavía no dispara ninguna consecuencia: la cuenta se queda en 0:00 y el texto lo
 * dice. Qué pasa después (cobro de espera, cancelación sin penalización) está por definirse.
 */
public class PickupWaitTimer {

    /** Ventana de cortesía. Es una constante de producto, no un parámetro del contrato. */
    public static final long WAIT_WINDOW_MS = 5 * 60 * 1000L;
    private static final long TICK_MS = 500L;

    private final View container;
    private final TextView countdown;
    private final TextView hint;
    private final ProgressBar progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @StringRes
    private final int hintRes;
    @StringRes
    private final int expiredRes;

    private final int countdownColor;
    private final android.content.res.ColorStateList progressTint;

    private long anchorMillis;
    private boolean running;
    private boolean expiredShown;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            if (running) {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    /**
     * @param root      cualquier vista que contenga el bloque incluido (la pantalla o el panel).
     * @param labelRes  título del bloque; cada lado lo nombra desde su punto de vista.
     * @param hintRes   línea de apoyo mientras corre el tiempo.
     * @param expiredRes línea que sustituye a la anterior al llegar a 0:00.
     */
    public PickupWaitTimer(@NonNull View root, @StringRes int labelRes, @StringRes int hintRes,
                            @StringRes int expiredRes) {
        this.container = root.findViewById(R.id.group_pickup_wait);
        this.countdown = root.findViewById(R.id.text_pickup_wait_countdown);
        this.hint = root.findViewById(R.id.text_pickup_wait_hint);
        this.progress = root.findViewById(R.id.progress_pickup_wait);
        this.hintRes = hintRes;
        this.expiredRes = expiredRes;
        this.countdownColor = countdown.getCurrentTextColor();
        this.progressTint = progress.getProgressTintList();
        ((TextView) root.findViewById(R.id.text_pickup_wait_label)).setText(labelRes);
    }

    /**
     * Muestra el bloque y arranca la cuenta. Llamar tantas veces como haga falta: mientras el
     * ancla no cambie, volver a llamar no reinicia nada —- el estado DRIVER_ARRIVED puede
     * repintarse varias veces (llega el dato del pasajero, se resuelve la dirección) y el
     * cronómetro no puede saltar hacia atrás por eso.
     */
    public void start(@Nullable String arrivedAtIso) {
        long anchor = parseMillis(arrivedAtIso);
        if (anchor <= 0) {
            anchor = running && anchorMillis > 0 ? anchorMillis : System.currentTimeMillis();
        }
        if (running && anchor == anchorMillis) {
            return;
        }
        anchorMillis = anchor;
        expiredShown = false;
        hint.setText(hintRes);
        countdown.setTextColor(countdownColor);
        progress.setProgressTintList(progressTint);
        container.setVisibility(View.VISIBLE);
        if (!running) {
            running = true;
            handler.post(ticker);
        } else {
            tick();
        }
    }

    /** Esconde el bloque y detiene la cuenta (el viaje arrancó, se canceló o se cerró la pantalla). */
    public void stop() {
        running = false;
        handler.removeCallbacks(ticker);
        container.setVisibility(View.GONE);
    }

    private void tick() {
        long remaining = Math.max(0, anchorMillis + WAIT_WINDOW_MS - System.currentTimeMillis());
        long seconds = (remaining + 999) / 1000;
        countdown.setText(String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60));
        progress.setProgress((int) (remaining * progress.getMax() / WAIT_WINDOW_MS));

        if (remaining == 0 && !expiredShown) {
            expiredShown = true;
            hint.setText(expiredRes);
            int error = container.getContext().getColor(R.color.drivo_error);
            countdown.setTextColor(error);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(error));
            // Ya no hay nada que contar: el bloque se queda en 0:00 a la vista, pero deja de
            // despertar al hilo principal cada medio segundo.
            running = false;
            handler.removeCallbacks(ticker);
        }
    }

    private static long parseMillis(@Nullable String isoTimestamp) {
        if (isoTimestamp == null) {
            return 0;
        }
        try {
            return Instant.parse(isoTimestamp).toEpochMilli();
        } catch (DateTimeParseException malFormado) {
            return 0;
        }
    }
}
