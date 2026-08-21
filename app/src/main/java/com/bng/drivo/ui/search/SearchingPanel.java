package com.bng.drivo.ui.search;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bng.drivo.R;
import com.bng.drivo.data.model.Offer;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.RealtimeSubscription;
import com.bng.drivo.data.repository.RideRealtimeRepository;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.ui.home.TripFlowViewModel;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

/**
 * Paso "Buscando conductores…" del flujo del pasajero: radar 1 a 1 sobre el canal en vivo de
 * rides/{id}/offers (ver openapi.yaml, "Canal en vivo"). Se muestra una sola tarjeta a la vez —
 * la de menor queue_position —, nunca una lista de ofertas simultáneas.
 *
 * <p>Era {@code SearchingDriverActivity}. El radar animado sigue centrado sobre el mapa, pero
 * ahora ese mapa es el mismo del home ({@link com.bng.drivo.ui.map.MapPresenter}) en vez de uno
 * recién creado, así que llegar aquí ya no repinta nada.
 *
 * <p>Cambio de comportamiento respecto a la Activity: salir de este paso <em>cancela el viaje</em>
 * en el servidor (POST /rides/{id}/cancel, previa confirmación). Antes el botón de cerrar sólo
 * hacía finish() y la solicitud se quedaba viva del lado del servidor, buscando conductor para un
 * pasajero que ya se había ido.
 */
public class SearchingPanel {

    public interface Callbacks {
        /** El pasajero aceptó una oferta: el host abre el viaje activo. */
        void onOfferAccepted(@NonNull Ride ride);

        /** Solicitud cancelada (o imposible de mantener): el host vuelve a Home. */
        void onSearchCancelled();
    }

    private static final long RADAR_PULSE_DURATION_MS = 1600L;

    private final View panel;
    private final View radarOverlay;
    private final TripFlowViewModel viewModel;
    private final TripRepository tripRepository;
    private final RideRealtimeRepository realtimeRepository;
    private final Callbacks callbacks;

    private final TextView textTitle;
    private final TextView textSubtitle;
    private final LinearLayout containerDrivers;
    private final MaterialButton btnCancelSearch;

    @Nullable
    private RealtimeSubscription offersSubscription;
    @Nullable
    private ValueAnimator radarAnimator;
    @Nullable
    private CountDownTimer expiryTimer;
    @Nullable
    private MaterialButton btnAcceptDriver;
    @Nullable
    private MaterialButton btnRejectDriver;

    private boolean active;
    private boolean actionInFlight;
    private boolean cancelling;

    public SearchingPanel(@NonNull View panel, @NonNull View radarOverlay,
                          @NonNull TripFlowViewModel viewModel, @NonNull TripRepository tripRepository,
                          @NonNull RideRealtimeRepository realtimeRepository,
                          @NonNull Callbacks callbacks) {
        this.panel = panel;
        this.radarOverlay = radarOverlay;
        this.viewModel = viewModel;
        this.tripRepository = tripRepository;
        this.realtimeRepository = realtimeRepository;
        this.callbacks = callbacks;

        textTitle = panel.findViewById(R.id.text_searching_title);
        textSubtitle = panel.findViewById(R.id.text_searching_subtitle);
        containerDrivers = panel.findViewById(R.id.container_drivers);
        btnCancelSearch = panel.findViewById(R.id.btn_cancel_search);
        btnCancelSearch.setOnClickListener(v -> confirmCancel());
    }

    private Context context() {
        return panel.getContext();
    }

    /** Entrada al paso: arranca el radar y se engancha al canal de ofertas. */
    public void show() {
        active = true;
        actionInFlight = false;
        cancelling = false;
        LoadingButtonHelper.setLoading(btnCancelSearch, false);
        showWaitingForNext();
        startRadarPulse();
        subscribe();
    }

    /** Salida del paso, por la razón que sea: deja de escuchar y para las animaciones. */
    public void hide() {
        active = false;
        unsubscribe();
        stopRadarPulse();
        cancelExpiryTimer();
        containerDrivers.removeAllViews();
        btnAcceptDriver = null;
        btnRejectDriver = null;
    }

    /**
     * Suscripción atada al ciclo de vida del host, no solo al paso: con la app en segundo plano
     * no hay a quién mostrarle una oferta, y dejar el listener de Firestore vivo solo gastaría.
     */
    public void onHostStart() {
        if (active) {
            startRadarPulse();
            subscribe();
        }
    }

    public void onHostStop() {
        unsubscribe();
        stopRadarPulse();
        cancelExpiryTimer();
    }

    private void subscribe() {
        String rideId = viewModel.getRideId();
        if (offersSubscription != null || rideId == null) {
            return;
        }
        offersSubscription = realtimeRepository.observeOffers(rideId, this::onOffersChanged);
    }

    private void unsubscribe() {
        if (offersSubscription != null) {
            offersSubscription.stop();
            offersSubscription = null;
        }
    }

    private void onOffersChanged(List<Offer> offers) {
        if (!active || actionInFlight || cancelling) {
            // Ya se disparó accept/reject/cancelar sobre la tarjeta actual; no la reemplaces a medias.
            return;
        }
        Offer current = offers.isEmpty() ? null : offers.get(0);
        if (current == null) {
            showWaitingForNext();
            return;
        }
        showOffer(current);
    }

    /** Sin oferta en pantalla: el radar late sobre el mapa y el modal solo informa. */
    private void showWaitingForNext() {
        cancelExpiryTimer();
        containerDrivers.removeAllViews();
        containerDrivers.setVisibility(View.GONE);
        btnAcceptDriver = null;
        btnRejectDriver = null;
        textTitle.setText(R.string.searching_message);
        textSubtitle.setText(R.string.searching_subtitle);
        radarOverlay.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    /** Con oferta: el radar estorba, la tarjeta pasa a ser lo único que importa. */
    private void showOffer(@NonNull Offer offer) {
        radarOverlay.setVisibility(View.GONE);
        textTitle.setText(R.string.searching_offer_received);
        textSubtitle.setText(context().getString(
                R.string.searching_counter, offer.getQueuePosition(), offer.getQueueTotal()));
        containerDrivers.removeAllViews();
        containerDrivers.setVisibility(View.VISIBLE);
        containerDrivers.addView(buildDriverCard(offer));
    }

    private View buildDriverCard(Offer offer) {
        View card = LayoutInflater.from(context())
                .inflate(R.layout.item_driver_offer, containerDrivers, false);

        String ratingText = offer.getDriverRating() != null
                ? String.format(Locale.getDefault(), " · ★%.1f", offer.getDriverRating()) : "";
        String vehicleText = joinNonNull(" ", offer.getVehicleBrand(), offer.getVehicleModel(),
                offer.getVehicleColor());
        String details = joinNonNull(" · ", vehicleText, offer.getVehiclePlate());

        // El contador ya lo lleva el subtítulo del panel; en la tarjeta sería repetirlo.
        card.findViewById(R.id.text_driver_counter).setVisibility(View.GONE);
        ((TextView) card.findViewById(R.id.text_driver_avatar)).setText(initialsFor(offer.getDriverName()));
        ((TextView) card.findViewById(R.id.text_driver_name)).setText(offer.getDriverName() + ratingText);
        ((TextView) card.findViewById(R.id.text_driver_details)).setText(details);
        ((TextView) card.findViewById(R.id.text_driver_price))
                .setText(String.format(Locale.getDefault(), "$%.2f", offer.getAmount()));

        TextView textEta = card.findViewById(R.id.text_driver_eta);
        if (offer.getEtaMin() != null) {
            textEta.setText(context().getString(R.string.searching_eta_min, offer.getEtaMin()));
            textEta.setVisibility(View.VISIBLE);
        } else {
            textEta.setVisibility(View.GONE);
        }

        startExpiryCountdown(card.findViewById(R.id.progress_offer_expiry), offer.getExpiresAtMillis());

        btnAcceptDriver = card.findViewById(R.id.btn_accept_driver);
        btnRejectDriver = card.findViewById(R.id.btn_reject_driver);
        btnAcceptDriver.setOnClickListener(v -> acceptOffer(offer));
        btnRejectDriver.setOnClickListener(v -> rejectOffer(offer));

        return card;
    }

    /**
     * Cosmético: el contrato es explícito en que la verdad es expires_at en el servidor, esta
     * barra solo comunica la urgencia — si nunca llega a 0 porque Firestore ya reemplazó la
     * tarjeta antes, no pasa nada.
     */
    private void startExpiryCountdown(ProgressBar progressBar, @Nullable Long expiresAtMillis) {
        cancelExpiryTimer();
        if (expiresAtMillis == null) {
            progressBar.setVisibility(View.GONE);
            return;
        }
        long totalMs = expiresAtMillis - System.currentTimeMillis();
        if (totalMs <= 0) {
            progressBar.setProgress(0);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setMax(1000);
        progressBar.setProgress(1000);

        expiryTimer = new CountDownTimer(totalMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                progressBar.setProgress((int) (1000 * millisUntilFinished / totalMs));
            }

            @Override
            public void onFinish() {
                progressBar.setProgress(0);
            }
        };
        expiryTimer.start();
    }

    private void cancelExpiryTimer() {
        if (expiryTimer != null) {
            expiryTimer.cancel();
            expiryTimer = null;
        }
    }

    private void acceptOffer(Offer offer) {
        actionInFlight = true;
        LoadingButtonHelper.setLoading(btnAcceptDriver, true);
        btnRejectDriver.setEnabled(false);
        String rideId = viewModel.getRideId();
        tripRepository.acceptOffer(rideId, offer.getOfferId(), new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                actionInFlight = false;
                if (active) {
                    callbacks.onOfferAccepted(ride);
                }
            }

            @Override
            public void onError(ApiException error) {
                actionInFlight = false;
                if (!active) {
                    return;
                }
                ApiErrorCode code = error.getCode();
                if (code == ApiErrorCode.DRIVER_NO_LONGER_AVAILABLE || code == ApiErrorCode.RIDE_ALREADY_TAKEN
                        || code == ApiErrorCode.OFFER_EXPIRED) {
                    // Sin diálogo de error por contrato: el listener de Firestore ya va a
                    // reemplazar esta tarjeta cuando el servidor actualice/borre el documento.
                    showWaitingForNext();
                    return;
                }
                LoadingButtonHelper.setLoading(btnAcceptDriver, false);
                btnRejectDriver.setEnabled(true);
                Toast.makeText(context(), R.string.searching_accept_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void rejectOffer(Offer offer) {
        actionInFlight = true;
        LoadingButtonHelper.setLoading(btnRejectDriver, true);
        btnAcceptDriver.setEnabled(false);
        String rideId = viewModel.getRideId();
        tripRepository.rejectOffer(rideId, offer.getOfferId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionInFlight = false;
                if (active) {
                    showWaitingForNext();
                }
            }

            @Override
            public void onError(ApiException error) {
                actionInFlight = false;
                if (!active) {
                    return;
                }
                LoadingButtonHelper.setLoading(btnRejectDriver, false);
                btnAcceptDriver.setEnabled(true);
                Toast.makeText(context(), R.string.searching_reject_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Punto único de salida del paso — lo llaman tanto el botón del modal como el "atrás" del
     * host (botón flotante o gesto del sistema), para que irse siempre implique la misma pregunta
     * y la misma cancelación en el servidor.
     */
    public void confirmCancel() {
        if (cancelling) {
            return;
        }
        new MaterialAlertDialogBuilder(context())
                .setTitle(R.string.active_trip_cancel_title)
                .setMessage(R.string.active_trip_cancel_message)
                .setNegativeButton(R.string.active_trip_cancel_negative, null)
                .setPositiveButton(R.string.active_trip_cancel_positive, (dialog, which) -> cancelRide())
                .show();
    }

    private void cancelRide() {
        String rideId = viewModel.getRideId();
        if (rideId == null) {
            callbacks.onSearchCancelled();
            return;
        }
        cancelling = true;
        LoadingButtonHelper.setLoading(btnCancelSearch, true);
        tripRepository.cancelRide(rideId, new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                cancelling = false;
                LoadingButtonHelper.setLoading(btnCancelSearch, false);
                Toast.makeText(context(), R.string.active_trip_cancelled_toast, Toast.LENGTH_SHORT).show();
                callbacks.onSearchCancelled();
            }

            @Override
            public void onError(ApiException error) {
                cancelling = false;
                LoadingButtonHelper.setLoading(btnCancelSearch, false);
                Toast.makeText(context(), R.string.active_trip_cancel_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Animación puramente decorativa (dos anillos que laten) — no depende de ningún dato. */
    private void startRadarPulse() {
        if (radarAnimator != null) {
            return;
        }
        View outer = radarOverlay.findViewById(R.id.radar_ring_outer);
        View inner = radarOverlay.findViewById(R.id.radar_ring_inner);

        radarAnimator = ValueAnimator.ofFloat(0f, 1f);
        radarAnimator.setDuration(RADAR_PULSE_DURATION_MS);
        radarAnimator.setRepeatCount(ValueAnimator.INFINITE);
        radarAnimator.setInterpolator(new LinearInterpolator());
        radarAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float scale = 0.85f + fraction * 0.3f;
            outer.setScaleX(scale);
            outer.setScaleY(scale);
            outer.setAlpha(0.2f * (1f - fraction));
            float innerFraction = (fraction + 0.5f) % 1f;
            float innerScale = 0.85f + innerFraction * 0.3f;
            inner.setScaleX(innerScale);
            inner.setScaleY(innerScale);
            inner.setAlpha(0.3f * (1f - innerFraction));
        });
        radarAnimator.start();
    }

    private void stopRadarPulse() {
        if (radarAnimator != null) {
            radarAnimator.cancel();
            radarAnimator = null;
        }
        if (!active) {
            radarOverlay.setVisibility(View.GONE);
        }
    }

    /** Compartidas con el host, que arma con ellas los extras del viaje activo. */
    public static String initialsFor(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length && initials.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return initials.toString();
    }

    public static String joinNonNull(String separator, String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                if (result.length() > 0) {
                    result.append(separator);
                }
                result.append(part);
            }
        }
        return result.toString();
    }
}
