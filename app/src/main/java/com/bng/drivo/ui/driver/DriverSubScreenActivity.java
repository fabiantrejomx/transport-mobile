package com.bng.drivo.ui.driver;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

/**
 * Base de las pantallas del conductor que cuelgan de Inicio (Ganancias, Configuración,
 * Seguridad): "atrás" siempre vuelve a Inicio, nunca sale de la app.
 *
 * <p>El comportamiento por omisión de Android —terminar la Activity y mostrar lo que haya
 * debajo— solo funciona si Inicio quedó realmente en la pila. Basta con que estas pantallas se
 * abran cuando no lo está (un push que entra directo, o un Inicio que se recreó por un cambio de
 * tema o porque el sistema recuperó memoria) para que "atrás" cierre la app en vez de volver.
 * Depender de la pila también deja la navegación a merced del camino que tomó el usuario, y
 * aquí la regla es fija: de estas tres pantallas siempre se vuelve a Inicio.
 *
 * <p>{@code isTaskRoot()} distingue los dos casos: si hay algo debajo (el caso normal, se llegó
 * desde el cajón de Inicio) basta con terminar; si no, se abre Inicio antes de terminar. Es el
 * mismo criterio que declara {@code parentActivityName} en el manifiesto, aplicado también al
 * botón atrás y no solo a la flecha "Up".
 */
public abstract class DriverSubScreenActivity extends AuthenticatedActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateHome();
            }
        });
    }

    /** La usan el botón atrás del sistema y la flecha de la propia pantalla, para que hagan lo mismo. */
    protected void navigateHome() {
        if (isTaskRoot()) {
            startActivity(new Intent(this, DriverHomeActivity.class));
        }
        finish();
    }
}
