package com.bng.drivo.ui.search;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Paso "Buscando conductores…" del flujo del pasajero: la subasta, sobre el canal en vivo de
 * rides/{id}/offers.
 *
 * <p>Antes esto mostraba una sola tarjeta a la vez (la de menor queue_position) y el pasajero
 * aceptaba o rechazaba esa oferta puntual. Ahora se ven <em>todas</em> las ofertas vivas a la vez
 * y el pasajero elige: el conductor acepta el viaje —contraofertando o no— y varios pueden
 * hacerlo en paralelo, pero la última palabra es del pasajero.
 *
 * <p>El servidor ya funcionaba así desde siempre: difunde el viaje por oleadas a varios
 * conductores (MatchingService), acepta una oferta por conductor y no deja cambiarla
 * (ALREADY_OFFERED), y publica la cola completa en Firestore con su queue_position/queue_total.
 * Lo único que imponía el "1 a 1" era esta pantalla.
 *
 * <p>Las tarjetas se ordenan por precio, de menor a mayor, no por orden de llegada: en una subasta
 * lo que se compara es el precio, y obligar a leerlas en el orden en que respondieron deja al
 * pasajero haciendo la comparación de cabeza mientras las ofertas vencen.
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
    private static final long EXPIRY_TICK_MS = 200L;
    /**
     * Cada cuánto se relee la subasta por HTTP. Corto porque es la ventana en la que el pasajero
     * decide (el TTL de una oferta ronda los 90 s) y una tarjeta que aparece tarde es una oferta
     * que se pierde; barato porque la búsqueda entera dura eso mismo y luego se apaga sola.
     */
    private static final long POLL_INTERVAL_MS = 3000L;
    /** El estado del viaje cambia mucho menos que la cola, así que se consulta uno de cada dos tics. */
    private static final int STATUS_POLL_EVERY_N_TICKS = 2;

    private final View panel;
    private final View radarOverlay;
    private final TripFlowViewModel viewModel;
    private final TripRepository tripRepository;
    private final RideRealtimeRepository realtimeRepository;
    private final Callbacks callbacks;

    private final TextView textTitle;
    private final TextView textSubtitle;
    private final TextView textStillLooking;
    private final View groupYourOffer;
    private final TextView textYourOffer;
    private final LinearLayout containerDrivers;
    private final MaterialButton btnCancelSearch;

    /** Lo que hace falta de cada tarjeta ya pintada: su barra de vencimiento y sus dos botones. */
    private static final class OfferCard {
        final Offer offer;
        final ProgressBar progress;
        final MaterialButton accept;
        final MaterialButton reject;
        final long totalMs;

        OfferCard(Offer offer, ProgressBar progress, MaterialButton accept, MaterialButton reject,
                   long totalMs) {
            this.offer = offer;
            this.progress = progress;
            this.accept = accept;
            this.reject = reject;
            this.totalMs = totalMs;
        }
    }

    @Nullable
    private RealtimeSubscription offersSubscription;
    @Nullable
    private RealtimeSubscription statusSubscription;
    @Nullable
    private ValueAnimator radarAnimator;
    /** Una tarjeta por oferta viva; se usa para refrescar sus barras y bloquearlas en bloque. */
    private final List<OfferCard> cards = new ArrayList<>();
    private final Handler expiryHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable expiryTick;
    /** Handler propio: stopExpiryTicker() vacía el suyo entero y se llevaría el sondeo por delante. */
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pollTick;
    private int pollTicks;
    /**
     * Firma de lo que hay pintado. Con dos fuentes alimentando la misma lista, la mayoría de las
     * actualizaciones no traen ningún cambio; repintar igual borraría y volvería a inflar las
     * tarjetas cada pocos segundos, con el parpadeo y las barras reiniciándose que eso implica.
     */
    @Nullable
    private String paintedSignature;

    private boolean active;
    private boolean actionInFlight;
    private boolean cancelling;
    /** Ya salimos por un estado terminal; evita repetir el aviso si llegan más eventos. */
    private boolean leaving;

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
        textStillLooking = panel.findViewById(R.id.text_searching_still_looking);
        groupYourOffer = panel.findViewById(R.id.group_searching_your_offer);
        textYourOffer = panel.findViewById(R.id.text_searching_your_offer);
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
        leaving = false;
        paintedSignature = null;
        LoadingButtonHelper.setLoading(btnCancelSearch, false);
        bindYourOffer();
        showWaitingForOffers();
        startRadarPulse();
        subscribe();
    }

    /** Salida del paso, por la razón que sea: deja de escuchar y para las animaciones. */
    public void hide() {
        active = false;
        unsubscribe();
        stopRadarPulse();
        clearCards();
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
        stopExpiryTicker();
    }

    private void subscribe() {
        String rideId = viewModel.getRideId();
        if (offersSubscription != null || rideId == null) {
            return;
        }
        offersSubscription = realtimeRepository.observeOffers(rideId, this::onOffersChanged);
        // También el estado del viaje, no solo las ofertas: la búsqueda tiene un plazo
        // (search_ttl_seconds) y cuando vence es el barredor del servidor quien la da por
        // muerta — nadie toca nada en el teléfono. Sin escuchar esto, el pasajero se quedaba
        // con "Buscando conductores…" para siempre sobre un viaje que ya no existía, y al
        // cancelar a mano recibía "ya se canceló" sin salir de la pantalla.
        statusSubscription = realtimeRepository.observeRideStatus(rideId, this::onRideStatusChanged);
        startPolling();
    }

    /**
     * La subasta releída por HTTP, en paralelo al canal en vivo.
     *
     * <p>No es un adorno defensivo: Firestore aquí es una <em>proyección</em> de la base del
     * servidor, no la verdad —lo dice el propio backend— y el pasajero se quedó más de una vez
     * mirando "buscando conductores" con ofertas ya creadas del otro lado. Basta con que la
     * proyección no llegue —una regla que niega la lectura, el documento del viaje aún sin
     * publicar, la red— para que la lista se vea vacía sin que nada falle a la vista: un canal
     * denegado se comporta igual que uno sin novedades.
     *
     * <p>Así la subasta depende de lo mismo que el resto de la app (la API), y el canal en vivo
     * queda como lo que es: el camino rápido. Cuando los dos funcionan, el segundo en llegar no
     * pinta nada — traen el mismo estado y la firma lo detecta.
     */
    private void startPolling() {
        stopPolling();
        pollTicks = 0;
        pollTick = this::poll;
        pollHandler.post(pollTick);
    }

    private void stopPolling() {
        pollHandler.removeCallbacksAndMessages(null);
        pollTick = null;
    }

    private void poll() {
        String rideId = viewModel.getRideId();
        if (!active || leaving || rideId == null) {
            return;
        }
        // El estado va primero: si el viaje ya venció, la cola llegaría vacía y eso es
        // indistinguible de "todavía nadie oferta".
        if (pollTicks % STATUS_POLL_EVERY_N_TICKS == 0) {
            tripRepository.getRideDetail(rideId, new ApiCallback<Ride>() {
                @Override
                public void onSuccess(Ride ride) {
                    if (ride != null && ride.getStatus() != null) {
                        onRideStatusChanged(ride.getStatus());
                    }
                }

                @Override
                public void onError(ApiException error) {
                    // Un tic perdido no cambia nada: el siguiente vuelve a preguntar.
                }
            });
        }
        pollTicks++;

        // El siguiente tic se agenda desde la respuesta, no aquí: con la red lenta, encadenarlos
        // por tiempo dejaría varias consultas en vuelo pisándose entre ellas.
        tripRepository.getOffers(rideId, new ApiCallback<List<Offer>>() {
            @Override
            public void onSuccess(List<Offer> offers) {
                onOffersChanged(offers);
                scheduleNextPoll();
            }

            @Override
            public void onError(ApiException error) {
                if (error.getCode() == ApiErrorCode.RIDE_NOT_FOUND) {
                    // El viaje dejó de existir para el servidor; no hay nada que seguir sondeando.
                    if (active && !leaving && !cancelling) {
                        leaveSearch(R.string.searching_no_longer_active_toast);
                    }
                    return;
                }
                scheduleNextPoll();
            }
        });
    }

    private void scheduleNextPoll() {
        if (!active || leaving || pollTick == null) {
            return;
        }
        pollHandler.postDelayed(pollTick, POLL_INTERVAL_MS);
    }

    private void unsubscribe() {
        stopPolling();
        if (offersSubscription != null) {
            offersSubscription.stop();
            offersSubscription = null;
        }
        if (statusSubscription != null) {
            statusSubscription.stop();
            statusSubscription = null;
        }
    }

    /**
     * Estados terminales de la búsqueda. MATCHED no se atiende aquí: ese lo produce nuestra propia
     * aceptación, que ya navega por su cuenta (ver {@link #acceptOffer}).
     */
    private void onRideStatusChanged(String status) {
        // cancelling: el propio pasajero está cancelando y ya tiene su aviso en camino; si el
        // evento de Firestore gana la carrera a la respuesta HTTP, avisarle aquí de que "ya no
        // está activa" contaría como ajeno algo que acaba de hacer él.
        if (!active || leaving || cancelling) {
            return;
        }
        switch (status) {
            case "EXPIRED_NO_DRIVERS":
                leaveSearch(R.string.searching_expired_toast);
                break;
            case "CANCELLED_BY_PASSENGER":
            case "CANCELLED_BY_DRIVER":
                // Cancelado desde otro lado (otro dispositivo, soporte): no hay nada que decidir.
                leaveSearch(R.string.searching_no_longer_active_toast);
                break;
            default:
                break;
        }
    }

    /** Salida por una razón ajena al pasajero: se avisa y se vuelve a Home. */
    private void leaveSearch(int messageRes) {
        leaving = true;
        Toast.makeText(context(), messageRes, Toast.LENGTH_LONG).show();
        callbacks.onSearchCancelled();
    }

    /**
     * Punto de entrada único de la lista, la traiga el canal en vivo o el sondeo por HTTP. Las dos
     * fuentes publican el mismo estado del servidor, así que no hay que conciliarlas: basta con
     * ignorar la que no traiga novedades.
     */
    private void onOffersChanged(List<Offer> offers) {
        if (!active || leaving || actionInFlight || cancelling) {
            // Hay un accept/reject/cancelar en vuelo sobre estas tarjetas; repintarlas a media
            // acción dejaría al pasajero tocando un botón que ya no corresponde a esa oferta.
            //
            // Lo que se descarta aquí no se pierde: la firma no se toca, así que la siguiente
            // actualización —del canal o del sondeo— vuelve a verlo como un cambio y lo pinta.
            return;
        }
        List<Offer> sorted = sortedByPrice(offers);
        String signature = signatureOf(sorted);
        if (signature.equals(paintedSignature)) {
            return;
        }
        paintedSignature = signature;
        if (sorted.isEmpty()) {
            showWaitingForOffers();
            return;
        }
        showOffers(sorted);
    }

    /**
     * Qué cuenta como "la misma lista ya pintada": las ofertas y su precio. El vencimiento queda
     * fuera a propósito — lo mueve el cronómetro por su cuenta, sin repintar nada.
     */
    private static String signatureOf(List<Offer> offers) {
        StringBuilder signature = new StringBuilder();
        for (Offer offer : offers) {
            signature.append(offer.getOfferId()).append('@').append(offer.getAmount()).append('|');
        }
        return signature.toString();
    }

    /**
     * De la más barata a la más cara, no por orden de llegada: en una subasta lo que se compara es
     * el precio, y obligar a leerlas en el orden en que respondieron deja al pasajero haciendo la
     * comparación de cabeza mientras las ofertas vencen.
     */
    private static List<Offer> sortedByPrice(List<Offer> offers) {
        List<Offer> sorted = new ArrayList<>(offers);
        Collections.sort(sorted, (a, b) -> {
            int byPrice = Double.compare(a.getAmount(), b.getAmount());
            if (byPrice != 0) {
                return byPrice;
            }
            // A igual precio decide quién llega antes; sin ETA, el orden de llegada del servidor.
            Integer etaA = a.getEtaMin();
            Integer etaB = b.getEtaMin();
            if (etaA != null && etaB != null && !etaA.equals(etaB)) {
                return Integer.compare(etaA, etaB);
            }
            return Integer.compare(a.getQueuePosition(), b.getQueuePosition());
        });
        return sorted;
    }

    /** Lo que el pasajero ofreció: la referencia contra la que se leen todas las tarjetas. */
    private void bindYourOffer() {
        double offered = viewModel.getOfferedFare();
        if (offered <= 0) {
            groupYourOffer.setVisibility(View.GONE);
            return;
        }
        groupYourOffer.setVisibility(View.VISIBLE);
        textYourOffer.setText(String.format(Locale.getDefault(), "$%.2f", offered));
    }

    /** Sin ofertas todavía: el radar late sobre el mapa y el modal solo informa. */
    private void showWaitingForOffers() {
        paintedSignature = "";
        clearCards();
        containerDrivers.setVisibility(View.GONE);
        textStillLooking.setVisibility(View.GONE);
        textTitle.setText(R.string.searching_message);
        textSubtitle.setText(R.string.searching_subtitle);
        radarOverlay.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    /**
     * La subasta: todas las ofertas vivas, ya ordenadas por precio. El radar se retira — con la
     * lista en pantalla el foco es elegir—, pero el aviso de que la búsqueda sigue abierta se
     * queda: el servidor no deja de difundir el viaje porque ya haya ofertas.
     */
    private void showOffers(List<Offer> sorted) {
        clearCards();
        radarOverlay.setVisibility(View.GONE);

        textTitle.setText(sorted.size() == 1
                ? context().getString(R.string.searching_offers_title_one)
                : context().getString(R.string.searching_offers_title, sorted.size()));
        textSubtitle.setText(R.string.searching_offers_subtitle);
        textStillLooking.setVisibility(View.VISIBLE);

        containerDrivers.setVisibility(View.VISIBLE);
        // El distintivo solo tiene sentido si hay contra qué comparar y el primero es de verdad
        // más barato: con dos ofertas iguales, señalar una sería arbitrario.
        boolean markBest = sorted.size() > 1 && sorted.get(0).getAmount() < sorted.get(1).getAmount();
        for (int i = 0; i < sorted.size(); i++) {
            containerDrivers.addView(buildDriverCard(sorted.get(i), markBest && i == 0));
        }
        startExpiryTicker();
    }

    private View buildDriverCard(Offer offer, boolean isBestPrice) {
        View card = LayoutInflater.from(context())
                .inflate(R.layout.item_driver_offer, containerDrivers, false);

        String ratingText = offer.getDriverRating() != null
                ? String.format(Locale.getDefault(), " · ★%.1f", offer.getDriverRating()) : "";
        String vehicleText = joinNonNull(" ", offer.getVehicleBrand(), offer.getVehicleModel(),
                offer.getVehicleColor());
        String details = joinNonNull(" · ", vehicleText, offer.getVehiclePlate());

        card.findViewById(R.id.text_driver_badge).setVisibility(isBestPrice ? View.VISIBLE : View.GONE);
        ((TextView) card.findViewById(R.id.text_driver_avatar)).setText(initialsFor(offer.getDriverName()));
        ((TextView) card.findViewById(R.id.text_driver_name)).setText(offer.getDriverName() + ratingText);
        ((TextView) card.findViewById(R.id.text_driver_details)).setText(details);
        ((TextView) card.findViewById(R.id.text_driver_price))
                .setText(String.format(Locale.getDefault(), "$%.2f", offer.getAmount()));
        bindPriceDelta(card.findViewById(R.id.text_driver_price_delta), offer);

        TextView textEta = card.findViewById(R.id.text_driver_eta);
        if (offer.getEtaMin() != null) {
            textEta.setText(context().getString(R.string.searching_eta_arrives, offer.getEtaMin()));
            textEta.setVisibility(View.VISIBLE);
        } else {
            textEta.setVisibility(View.GONE);
        }

        MaterialButton accept = card.findViewById(R.id.btn_accept_driver);
        MaterialButton reject = card.findViewById(R.id.btn_reject_driver);
        accept.setOnClickListener(v -> acceptOffer(offer, accept));
        reject.setOnClickListener(v -> rejectOffer(offer, reject));

        ProgressBar progress = card.findViewById(R.id.progress_offer_expiry);
        long totalMs = 0;
        Long expiresAt = offer.getExpiresAtMillis();
        if (expiresAt == null) {
            progress.setVisibility(View.GONE);
        } else {
            totalMs = expiresAt - System.currentTimeMillis();
            progress.setVisibility(totalMs > 0 ? View.VISIBLE : View.GONE);
        }
        cards.add(new OfferCard(offer, progress, accept, reject, totalMs));

        return card;
    }

    /**
     * Cuánto pide el conductor por encima de lo que el pasajero ofreció. El contrato no deja
     * contraofertar por debajo, así que solo hay dos casos: aceptó la tarifa tal cual, o pide más.
     */
    private void bindPriceDelta(TextView view, Offer offer) {
        double offered = viewModel.getOfferedFare();
        double delta = offer.getAmount() - offered;
        if (offered <= 0) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(delta <= 0.009
                ? context().getString(R.string.searching_price_delta_same)
                : context().getString(R.string.searching_price_delta_over, delta));
    }

    /**
     * Un solo tic para todas las barras en vez de un CountDownTimer por tarjeta: cada oferta vence
     * por su cuenta (el TTL corre desde que ese conductor ofertó), y con N temporizadores sueltos
     * era cuestión de tiempo que alguno sobreviviera a su tarjeta.
     *
     * <p>Cosmético: la verdad es el {@code expires_at} del servidor. Si una barra llega a cero
     * antes de que Firestore retire la tarjeta, no pasa nada — el intento fallará limpio con
     * OFFER_EXPIRED.
     */
    private void startExpiryTicker() {
        stopExpiryTicker();
        expiryTick = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (OfferCard card : cards) {
                    Long expiresAt = card.offer.getExpiresAtMillis();
                    if (expiresAt == null || card.totalMs <= 0) {
                        continue;
                    }
                    long remaining = Math.max(0, expiresAt - now);
                    card.progress.setProgress((int) (1000 * remaining / card.totalMs));
                }
                expiryHandler.postDelayed(this, EXPIRY_TICK_MS);
            }
        };
        expiryHandler.post(expiryTick);
    }

    private void stopExpiryTicker() {
        expiryHandler.removeCallbacksAndMessages(null);
        expiryTick = null;
    }

    private void clearCards() {
        stopExpiryTicker();
        cards.clear();
        containerDrivers.removeAllViews();
    }

    /** Mientras una acción está en vuelo, ninguna otra tarjeta acepta toques. */
    private void setCardsEnabled(boolean enabled) {
        for (OfferCard card : cards) {
            card.accept.setEnabled(enabled);
            card.reject.setEnabled(enabled);
        }
    }

    private void acceptOffer(Offer offer, MaterialButton button) {
        actionInFlight = true;
        setCardsEnabled(false);
        LoadingButtonHelper.setLoading(button, true);
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
                LoadingButtonHelper.setLoading(button, false);
                setCardsEnabled(true);
                if (code == ApiErrorCode.DRIVER_NO_LONGER_AVAILABLE || code == ApiErrorCode.RIDE_ALREADY_TAKEN
                        || code == ApiErrorCode.OFFER_EXPIRED) {
                    // Se lo llevó otro o venció mientras el pasajero decidía. Sin diálogo de
                    // error por contrato: el listener de Firestore ya va a retirar esa tarjeta,
                    // y las demás siguen siendo elegibles.
                    Toast.makeText(context(), R.string.searching_offer_gone, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(context(), R.string.searching_accept_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Descartar a un conductor concreto sin salir de la subasta: su tarjeta desaparece de la lista
     * (el servidor la marca rechazada y le limpia la bandeja, así que vuelve al radar sin
     * penalización) y las demás siguen ahí.
     */
    private void rejectOffer(Offer offer, MaterialButton button) {
        actionInFlight = true;
        setCardsEnabled(false);
        LoadingButtonHelper.setLoading(button, true);
        String rideId = viewModel.getRideId();
        tripRepository.rejectOffer(rideId, offer.getOfferId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionInFlight = false;
                // No se repinta a mano: el servidor republica la cola sin esa oferta y el listener
                // de Firestore la retira. Repintar aquí duplicaría la verdad.
                LoadingButtonHelper.setLoading(button, false);
                setCardsEnabled(true);
            }

            @Override
            public void onError(ApiException error) {
                actionInFlight = false;
                if (!active) {
                    return;
                }
                LoadingButtonHelper.setLoading(button, false);
                setCardsEnabled(true);
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
                ApiErrorCode code = error.getCode();
                // El viaje ya no está en un estado cancelable —lo más común: venció mientras el
                // pasajero decidía—. Insistir no sirve de nada y quedarse aquí, menos: la única
                // salida honesta es volver a Home, que es justo lo que el pasajero pedía.
                if (code == ApiErrorCode.INVALID_STATE_TRANSITION || code == ApiErrorCode.RIDE_NOT_FOUND) {
                    leaveSearch(R.string.searching_no_longer_active_toast);
                    return;
                }
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
