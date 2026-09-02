package com.bng.drivo.ui.driver;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bng.drivo.R;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.model.Wallet;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.util.DrawerInsets;
import com.google.android.material.navigation.NavigationView;

/**
 * Base de las secciones del conductor que cuelgan de Inicio (Ganancias y Configuración). Les da dos
 * cosas: el cajón de navegación y la regla de que "atrás" siempre vuelve a Inicio.
 *
 * <p>Esto es solo para las secciones <b>del cajón</b>. Seguridad no lo es —se llega por su fila
 * dentro de Configuración, igual que en el pasajero— y por eso es una Activity normal con flecha de
 * volver: darle cajón la convertiría en un destino con dos puertas, y entonces habría que decidir
 * cuál de las dos queda marcada al abrir el menú.</p>
 *
 * <p><b>El cajón.</b> Es el menú general de la app, así que tiene que poder abrirse desde cualquier
 * sección y no solo desde Inicio — de ahí que estas pantallas lleven hamburguesa y no flecha. Como
 * aquí cada sección es una Activity propia (y no una pestaña de una sola Activity, como en el lado
 * del pasajero), cada una necesita su propia instancia: un DrawerLayout solo abre el suyo. Para no
 * repetirlo en tres layouts, {@link #setContentView(int)} envuelve el contenido de la pantalla en
 * activity_driver_subscreen.xml; las subclases siguen llamando a setContentView con su layout de
 * siempre y no se enteran.
 *
 * <p>Moverse entre secciones <b>sustituye</b> la pantalla en vez de apilarla ({@code finish()} tras
 * abrir la nueva): son hermanas, no una dentro de otra, y sin eso ir de Ganancias a Configuración y
 * a Seguridad dejaría tres Activities vivas que hay que atravesar una a una para volver a Inicio.
 *
 * <p><b>Atrás.</b> El comportamiento por omisión de Android —terminar la Activity y mostrar lo que
 * haya debajo— solo funciona si Inicio quedó realmente en la pila. Basta con que estas pantallas se
 * abran cuando no lo está (un push que entra directo, o un Inicio que se recreó por un cambio de
 * tema o porque el sistema recuperó memoria) para que "atrás" cierre la app en vez de volver.
 * Depender de la pila también deja la navegación a merced del camino que tomó el usuario, y aquí la
 * regla es fija: de estas tres pantallas siempre se vuelve a Inicio. {@code isTaskRoot()} distingue
 * los dos casos.
 */
public abstract class DriverSubScreenActivity extends AuthenticatedActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private View navHeader;
    private OnBackPressedCallback backCallback;

    /** El item del cajón que representa a esta pantalla, para marcarlo como el sitio donde estás. */
    @IdRes
    protected abstract int navMenuItemId();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Con el cajón abierto, atrás lo cierra: DrawerLayout no lo hace solo, y sin esto
                // el gesto saltaría a Inicio desde un menú abierto, que no es lo que se pidió.
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                navigateHome();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    /**
     * Infla el layout de la pantalla dentro del marco con cajón. Las subclases llaman a esto con su
     * propio layout, como antes; la envoltura es invisible para ellas salvo por
     * {@link #openDrawer()}, que es lo que tienen que enganchar a su hamburguesa.
     */
    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(R.layout.activity_driver_subscreen);
        getLayoutInflater().inflate(layoutResID, findViewById(R.id.subscreen_content), true);
        setUpDrawer();
    }

    protected void openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    private void setUpDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        navHeader = navView.getHeaderView(0);
        int navHeaderBasePaddingPx = navHeader.getPaddingTop();

        // A diferencia de Inicio, aquí sí se marca la sección actual: es la única forma de que el
        // cajón conteste "dónde estoy" cuando lo abres desde una pantalla que no es Inicio.
        navView.setCheckedItem(navMenuItemId());
        navView.setNavigationItemSelectedListener(item -> onNavItemSelected(item.getItemId()));
        // La cabecera resume el dinero; el detalle real (libro contable) vive en Ganancias.
        navHeader.setOnClickListener(v -> onNavItemSelected(R.id.nav_driver_earnings));

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                // NavigationView deja de compensar la status bar en cuanto hay cabecera; sin esto
                // el nombre quedaría pegado al reloj — ver DrawerInsets.
                DrawerInsets.applyTopInset(navHeader, navHeaderBasePaddingPx);
            }
        });

        loadNavHeader();
    }

    /**
     * @return true solo para la sección en la que ya estamos, que es la única que se queda marcada.
     *     Las demás abren su pantalla y esta muere, así que marcarlas señalaría un sitio del que ya
     *     nos vamos.
     */
    private boolean onNavItemSelected(@IdRes int id) {
        drawerLayout.closeDrawer(GravityCompat.START);
        if (id == navMenuItemId()) {
            return true;
        }
        if (id == R.id.nav_driver_inicio) {
            navigateHome();
            return false;
        }
        Class<?> target = activityFor(id);
        if (target != null) {
            startActivity(new Intent(this, target));
            finish();
        }
        return false;
    }

    @Nullable
    private Class<?> activityFor(@IdRes int id) {
        if (id == R.id.nav_driver_earnings) {
            return DriverEarningsActivity.class;
        }
        if (id == R.id.nav_driver_settings) {
            return DriverSettingsActivity.class;
        }
        return null;
    }

    /**
     * Identidad, calificación, saldo y viajes de hoy. Se piden aquí y no en cada sección porque la
     * cabecera es parte del cajón, no de la pantalla: una sección que no los pidiera abriría el
     * mismo menú con los huecos vacíos.
     */
    private void loadNavHeader() {
        UserRepository userRepository = new RestUserRepository(this);
        DriverRepository driverRepository = new RestDriverRepository(this);

        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                DriverNavHeader.applyProfile(navHeader, profile, driverRepository);
            }

            @Override
            public void onError(ApiException error) {
                // Sin nombre se queda el placeholder del layout; el menú sigue siendo usable.
            }
        });

        driverRepository.getWallet(new ApiCallback<Wallet>() {
            @Override
            public void onSuccess(Wallet wallet) {
                DriverNavHeader.applyWallet(DriverSubScreenActivity.this, navHeader, wallet);
            }

            @Override
            public void onError(ApiException error) {
                DriverNavHeader.applyWalletError(DriverSubScreenActivity.this, navHeader);
            }
        });
    }

    /** La usan el botón atrás del sistema y el item "Inicio" del cajón, para que hagan lo mismo. */
    protected void navigateHome() {
        if (isTaskRoot()) {
            startActivity(new Intent(this, DriverHomeActivity.class));
        }
        finish();
    }
}
