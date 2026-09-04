package com.bng.drivo.ui.price;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.bng.drivo.R;
import com.bng.drivo.data.model.Quote;
import com.bng.drivo.data.model.Ride;
import com.bng.drivo.data.model.Waypoint;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.TripRepository;
import com.bng.drivo.ui.home.TripFlowViewModel;
import com.bng.drivo.ui.map.MapPresenter;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.Locale;

/**
 * Paso "Confirma tu viaje" del flujo del pasajero: tarifa negociable sobre la ruta ya dibujada.
 *
 * <p>Era {@code ConfirmPriceActivity}. Al dejar de ser una pantalla propia se quedó sin mapa
 * propio (lo pone {@link MapPresenter}, compartido con el home y con el radar) y sin extras de
 * Intent (los datos del viaje viven en {@link TripFlowViewModel}); lo demás — cotización, banda
 * de negociación y creación del viaje — es la misma lógica, intacta.
 *
 * <p>La tarifa sugerida y los límites floor/ceiling vienen siempre de POST /quotes: el cliente
 * nunca calcula distancia ni precio (ver openapi.yaml).
 */
public class ConfirmPricePanel {

    public interface Callbacks {
        /** POST /rides respondió: el host pasa al radar. */
        void onRideCreated(@NonNull Ride ride);

        /** Sin cotización no hay nada que confirmar: el host vuelve a Home. */
        void onQuoteFailed();

        /** "+ Añadir parada" tocado: el host pasa al paso de elegirla sobre el mapa. */
        void onAddStopRequested();

        /** "Cancelar": se descarta la solicitud y el host vuelve a Home. */
        void onTripCancelled();
    }

    /** El Slider de Material exige que (max - min) sea múltiplo exacto de este paso. */
    private static final float PRICE_STEP = 2f;

    private final View panel;
    private final View routeCard;
    private final TripFlowViewModel viewModel;
    private final MapPresenter mapPresenter;
    private final TripRepository tripRepository;
    private final Callbacks callbacks;

    private final TextView textOrigin;
    private final TextView textDestination;
    private final TextView textDestinationAddress;
    private final TextView textAddStop;
    private final View btnRemoveStop;
    private final TextView textPriceAmount;
    private final TextView textPriceMin;
    private final TextView textPriceMax;
    private final Slider slider;
    private final View progressQuote;
    private final MaterialButton btnRequestTrip;
    private final MaterialButton btnCancelTrip;

    private boolean requestingRide;

    public ConfirmPricePanel(@NonNull View panel, @NonNull View routeCard,
                             @NonNull TripFlowViewModel viewModel, @NonNull MapPresenter mapPresenter,
                             @NonNull TripRepository tripRepository,
                             @NonNull Callbacks callbacks) {
        this.panel = panel;
        this.routeCard = routeCard;
        this.viewModel = viewModel;
        this.mapPresenter = mapPresenter;
        this.tripRepository = tripRepository;
        this.callbacks = callbacks;

        textOrigin = routeCard.findViewById(R.id.text_origin);
        textDestination = routeCard.findViewById(R.id.text_destination);
        textDestinationAddress = routeCard.findViewById(R.id.text_destination_address);
        textAddStop = routeCard.findViewById(R.id.text_add_stop);
        btnRemoveStop = routeCard.findViewById(R.id.btn_remove_stop);
        textPriceAmount = panel.findViewById(R.id.text_price_amount);
        textPriceMin = panel.findViewById(R.id.text_price_min);
        textPriceMax = panel.findViewById(R.id.text_price_max);
        slider = panel.findViewById(R.id.slider_price);
        progressQuote = panel.findViewById(R.id.progress_quote);
        btnRequestTrip = panel.findViewById(R.id.btn_request_trip);
        btnCancelTrip = panel.findViewById(R.id.btn_cancel_trip);

        btnCancelTrip.setOnClickListener(v -> callbacks.onTripCancelled());
        slider.addOnChangeListener((s, value, fromUser) -> textPriceAmount.setText(formatPrice(value)));
        panel.findViewById(R.id.row_payment).setOnClickListener(v ->
                Toast.makeText(context(), R.string.confirm_price_payment_coming_soon, Toast.LENGTH_SHORT).show());
        btnRequestTrip.setOnClickListener(v -> requestRide());
        routeCard.findViewById(R.id.row_add_stop).setOnClickListener(v -> onStopRowClicked());
        btnRemoveStop.setOnClickListener(v -> removeStop());
    }

    private Context context() {
        return panel.getContext();
    }

    /**
     * Entrada al paso: pinta la ruta en el mapa compartido y pide la cotización. Se vuelve a
     * llamar si el usuario elige otro destino sin haber salido del paso.
     */
    public void show() {
        requestingRide = false;
        btnCancelTrip.setEnabled(true);
        LoadingButtonHelper.setLoading(btnRequestTrip, false);
        bindRouteSummary();
        mapPresenter.showRoutePending(viewModel.getRoutePoints());
        loadQuote();
    }

    private void onStopRowClicked() {
        if (viewModel.getStop() != null) {
            return;
        }
        callbacks.onAddStopRequested();
    }

    /**
     * Igual que {@link #show()} pero sin resetear el slider ni el spinner de golpe: se llama al
     * volver del paso PICK_STOP con una parada nueva ya guardada en el ViewModel. Origen y
     * destino no cambiaron, así que no hace falta repintar esas filas — solo la parada, la ruta
     * (ahora con el punto intermedio) y la cotización, que sí depende de la distancia real.
     */
    public void refreshAfterStopChange() {
        bindStopRow();
        // Sin el trazo de la cotización anterior: describía la ruta sin esta parada, y pintarlo
        // ahora sería enseñar un recorrido que ya no es el del viaje. Lo repone bindQuote().
        mapPresenter.showRoutePending(viewModel.getRoutePoints());
        loadQuote();
    }

    private void removeStop() {
        viewModel.setStop(null);
        bindStopRow();
        // Mismo motivo que en refreshAfterStopChange(): el trazo vigente todavía pasa por la
        // parada que se acaba de quitar.
        mapPresenter.showRoutePending(viewModel.getRoutePoints());
        loadQuote();
    }

    /**
     * El destino en la tarjeta de ruta.
     *
     * <p>Cuando salió de una dirección guardada manda su nombre —"Casa", "Trabajo"— y la dirección
     * pasa a una segunda línea debajo. El nombre solo no basta: dice cuál de sus direcciones eligió
     * el pasajero, pero no a dónde va, y en esta tarjeta hace falta lo uno y lo otro. Elegido de
     * cualquier otra forma no hay nombre que enseñar y la fila se queda de una línea, como estaba.
     */
    /**
     * Vuelca en la tarjeta de ruta lo que el ViewModel sabe del viaje: origen, parada y destino.
     *
     * <p>Es público porque la tarjeta le sobrevive al panel: sigue en pantalla durante la subasta,
     * cuando este panel ya se escondió. Sin que el host la repueble, volver a SEARCHING con la
     * vista recién creada —una rotación a media espera— dejaba la tarjeta con sus TextView vacíos:
     * un "Viaje solicitado" sin decir qué viaje.
     */
    public void bindRouteSummary() {
        textOrigin.setText(viewModel.getOriginText());
        bindDestination();
        bindStopRow();
    }

    private void bindDestination() {
        String label = viewModel.getDestinationLabel();
        if (label == null || label.trim().isEmpty()) {
            textDestination.setText(viewModel.getDestinationText());
            textDestination.setTypeface(null, Typeface.NORMAL);
            textDestinationAddress.setVisibility(View.GONE);
            return;
        }
        textDestination.setText(label);
        // Negrita como en item_saved_address: la tarjeta repite el mismo par nombre/dirección de
        // la fila que el pasajero acaba de tocar, y reconocerlo es justo lo que se busca.
        textDestination.setTypeface(null, Typeface.BOLD);
        textDestinationAddress.setText(viewModel.getDestinationText());
        textDestinationAddress.setVisibility(View.VISIBLE);
    }

    private void bindStopRow() {
        Waypoint stop = viewModel.getStop();
        if (stop != null) {
            textAddStop.setText(stop.getText());
            textAddStop.setTextColor(context().getColor(R.color.drivo_secondary));
            btnRemoveStop.setVisibility(View.VISIBLE);
        } else {
            textAddStop.setText(R.string.confirm_price_add_stop);
            textAddStop.setTextColor(context().getColor(R.color.drivo_success));
            btnRemoveStop.setVisibility(View.GONE);
        }
    }

    private void loadQuote() {
        setFormEnabled(false);
        progressQuote.setVisibility(View.VISIBLE);

        tripRepository.createQuote(originLat(), originLng(), destinationLat(), destinationLng(),
                viewModel.getOriginText(), viewModel.getDestinationText(), viewModel.getWaypoints(),
                new ApiCallback<Quote>() {
                    @Override
                    public void onSuccess(Quote quote) {
                        progressQuote.setVisibility(View.INVISIBLE);
                        bindQuote(quote);
                    }

                    @Override
                    public void onError(ApiException error) {
                        progressQuote.setVisibility(View.INVISIBLE);
                        Toast.makeText(context(), R.string.confirm_price_quote_error, Toast.LENGTH_SHORT).show();
                        callbacks.onQuoteFailed();
                    }
                });
    }

    private void bindQuote(Quote quote) {
        viewModel.setQuote(quote);
        // El trazo por calles viaja en la cotización, así que este es el primer momento en que se
        // puede pintar. Si el servidor no lo mandó, showRoute cae a la guía recta.
        mapPresenter.showRoute(viewModel.getRoutePoints(), quote.getPolyline());

        float min = Math.round(quote.getFloor());
        float max = Math.round(quote.getCeiling());
        max -= (max - min) % PRICE_STEP;
        if (max <= min) {
            max = min + PRICE_STEP;
        }

        int floorPercent = quote.getSuggestedFare() > 0
                ? Math.round((float) (quote.getFloor() / quote.getSuggestedFare()) * 100) : 80;
        int ceilingPercent = quote.getSuggestedFare() > 0
                ? Math.round((float) (quote.getCeiling() / quote.getSuggestedFare()) * 100) : 140;

        textPriceMin.setText(formatMinMax(min, floorPercent));
        textPriceMax.setText(formatMinMax(max, ceilingPercent));

        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(PRICE_STEP);
        float snappedValue = snapToStep((float) quote.getSuggestedFare(), min, max, PRICE_STEP);
        slider.setValue(snappedValue);
        textPriceAmount.setText(formatPrice(snappedValue));

        setFormEnabled(true);
    }

    /**
     * El Slider de Material exige que todo valor asignado caiga exacto en la grilla
     * {@code min, min+stepSize, min+2*stepSize, ...} o lanza IllegalArgumentException — la
     * tarifa sugerida del servidor viene en pesos con centavos y casi nunca cae ahí sola.
     */
    private float snapToStep(float value, float min, float max, float stepSize) {
        float clamped = Math.max(min, Math.min(max, value));
        long steps = Math.round((clamped - min) / stepSize);
        return min + steps * stepSize;
    }

    private void requestRide() {
        Quote quote = viewModel.getQuote();
        if (quote == null || requestingRide) {
            return;
        }
        requestingRide = true;
        setFormEnabled(false);
        // Cancelar sigue disponible mientras se cotiza, pero no mientras POST /rides está en
        // vuelo: salir justo ahí dejaría el viaje ya creado en el servidor sin nadie mirándolo.
        btnCancelTrip.setEnabled(false);
        LoadingButtonHelper.setLoading(btnRequestTrip, true);

        tripRepository.createRide(quote.getId(), slider.getValue(), new ApiCallback<Ride>() {
            @Override
            public void onSuccess(Ride ride) {
                deliverRide(ride);
            }

            @Override
            public void onError(ApiException error) {
                if (error.getCode() == ApiErrorCode.QUOTE_EXPIRED) {
                    recotizeAndRetry();
                    return;
                }
                failRequest();
            }
        });
    }

    /** QUOTE_EXPIRED se resuelve recotizando una vez y reintentando, nunca como error crudo al usuario. */
    private void recotizeAndRetry() {
        float previousOffer = slider.getValue();
        tripRepository.createQuote(originLat(), originLng(), destinationLat(), destinationLng(),
                viewModel.getOriginText(), viewModel.getDestinationText(), viewModel.getWaypoints(),
                new ApiCallback<Quote>() {
                    @Override
                    public void onSuccess(Quote quote) {
                        bindQuote(quote);
                        // bindQuote() reactiva el botón como si fuera la carga inicial del paso —
                        // todavía estamos a mitad del reintento automático.
                        LoadingButtonHelper.setLoading(btnRequestTrip, true);
                        // Conservar la oferta que el pasajero ya había elegido, no la nueva
                        // tarifa sugerida — bindQuote() la pisó al reconstruir el slider.
                        float retryOffer = snapToStep(previousOffer, slider.getValueFrom(),
                                slider.getValueTo(), slider.getStepSize());
                        slider.setValue(retryOffer);
                        textPriceAmount.setText(formatPrice(retryOffer));

                        tripRepository.createRide(quote.getId(), retryOffer, new ApiCallback<Ride>() {
                            @Override
                            public void onSuccess(Ride ride) {
                                deliverRide(ride);
                            }

                            @Override
                            public void onError(ApiException error) {
                                failRequest();
                            }
                        });
                    }

                    @Override
                    public void onError(ApiException error) {
                        failRequest();
                    }
                });
    }

    private void deliverRide(@NonNull Ride ride) {
        viewModel.setRideId(ride.getId());
        viewModel.setOfferedFare(slider.getValue());
        requestingRide = false;
        LoadingButtonHelper.setLoading(btnRequestTrip, false);
        callbacks.onRideCreated(ride);
    }

    private void failRequest() {
        requestingRide = false;
        setFormEnabled(true);
        btnCancelTrip.setEnabled(true);
        LoadingButtonHelper.setLoading(btnRequestTrip, false);
        Toast.makeText(context(), R.string.confirm_price_request_error, Toast.LENGTH_SHORT).show();
    }

    private double originLat() {
        return viewModel.getOrigin() != null ? viewModel.getOrigin().latitude : 0;
    }

    private double originLng() {
        return viewModel.getOrigin() != null ? viewModel.getOrigin().longitude : 0;
    }

    private double destinationLat() {
        return viewModel.getDestination() != null ? viewModel.getDestination().latitude : 0;
    }

    private double destinationLng() {
        return viewModel.getDestination() != null ? viewModel.getDestination().longitude : 0;
    }

    private void setFormEnabled(boolean enabled) {
        slider.setEnabled(enabled);
        btnRequestTrip.setEnabled(enabled);
    }

    private String formatPrice(float value) {
        return String.format(Locale.getDefault(), "$%.2f", value);
    }

    private String formatMinMax(float value, int percent) {
        return String.format(Locale.getDefault(), "$%.0f (%d%%)", value, percent);
    }
}
