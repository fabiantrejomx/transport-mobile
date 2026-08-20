package com.bng.drivo.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.bng.drivo.ui.auth.AuthenticatedActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.bng.drivo.R;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DeviceRepository;
import com.bng.drivo.data.repository.RestDeviceRepository;
import com.bng.drivo.ui.settings.ConfiguracionesFragment;
import com.bng.drivo.ui.trips.ViajesFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Host de las 3 secciones del pasajero (Inicio / Viajes / Configuración), con un drawer
 * lateral en vez de barra inferior — Inicio ahora es un mapa a pantalla completa con un modal
 * persistente, sin espacio para una barra de navegación fija. Perfil se fusionó dentro de
 * Configuración (identidad + ajustes en una sola pantalla). Mismo patrón show/hide de antes:
 * cambiar de sección solo intercambia el contenido del contenedor.
 */
public class HomeActivity extends AuthenticatedActivity {

    private static final String TAG_HOME = "tab_home";
    private static final String TAG_VIAJES = "tab_viajes";
    private static final String TAG_CONFIGURACIONES = "tab_configuraciones";

    private Fragment homeFragment;
    private Fragment viajesFragment;
    private Fragment configuracionesFragment;
    private Fragment activeFragment;
    private DrawerLayout drawerLayout;
    private NavigationView navView;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> registerFcmToken());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        FragmentManager fragmentManager = getSupportFragmentManager();

        if (savedInstanceState != null) {
            homeFragment = fragmentManager.findFragmentByTag(TAG_HOME);
            viajesFragment = fragmentManager.findFragmentByTag(TAG_VIAJES);
            configuracionesFragment = fragmentManager.findFragmentByTag(TAG_CONFIGURACIONES);
        }
        if (homeFragment == null) {
            homeFragment = new HomeFragment();
        }
        if (viajesFragment == null) {
            viajesFragment = new ViajesFragment();
        }
        if (configuracionesFragment == null) {
            configuracionesFragment = new ConfiguracionesFragment();
        }

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        addIfNeeded(transaction, fragmentManager, homeFragment, TAG_HOME);
        addIfNeeded(transaction, fragmentManager, viajesFragment, TAG_VIAJES);
        addIfNeeded(transaction, fragmentManager, configuracionesFragment, TAG_CONFIGURACIONES);
        transaction.show(homeFragment).hide(viajesFragment).hide(configuracionesFragment);
        transaction.commitNow();
        activeFragment = homeFragment;

        setUpDrawer();
    }

    private void addIfNeeded(FragmentTransaction transaction, FragmentManager fragmentManager,
                              Fragment fragment, String tag) {
        if (fragmentManager.findFragmentByTag(tag) == null) {
            transaction.add(R.id.fragment_container, fragment, tag);
        }
    }

    private void setUpDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        navView.setCheckedItem(R.id.nav_inicio);
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            boolean handled = true;
            if (id == R.id.nav_inicio) {
                showTab(homeFragment);
            } else if (id == R.id.nav_viajes) {
                showTab(viajesFragment);
            } else if (id == R.id.nav_configuraciones) {
                showTab(configuracionesFragment);
            } else {
                handled = false;
            }
            if (handled) {
                // setCheckedItem() (no item.setChecked()) es el que de verdad desmarca las
                // demás opciones — sin group checkableBehavior="single" en el menú,
                // item.setChecked(true) solo prendía la tocada y las anteriores se quedaban
                // marcadas también, hasta que las 3 terminaban resaltadas.
                navView.setCheckedItem(id);
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return handled;
        });
    }

    /** La llaman los botones hamburguesa de cada Fragment hijo (Home, Viajes, Configuración). */
    public void openDrawer() {
        navView.setCheckedItem(idForActiveFragment());
        drawerLayout.openDrawer(GravityCompat.START);
    }

    /** Para que el menú siempre abra mostrando la sección real, no solo la que estaba
     * marcada la última vez que se tocó una opción del propio menú. */
    private int idForActiveFragment() {
        if (activeFragment == viajesFragment) {
            return R.id.nav_viajes;
        }
        if (activeFragment == configuracionesFragment) {
            return R.id.nav_configuraciones;
        }
        return R.id.nav_inicio;
    }

    private void showTab(Fragment target) {
        if (target != activeFragment) {
            getSupportFragmentManager().beginTransaction()
                    .hide(activeFragment)
                    .show(target)
                    .commit();
            activeFragment = target;
        }
    }

    /**
     * La llama HomeFragment una vez que su propio flujo de permiso de ubicación termina
     * (otorgado, negado o ya resuelto de antes) — nunca desde onCreate() directamente. Android
     * solo permite un diálogo de permiso pendiente a la vez: si dos ActivityResultLauncher
     * lanzan un requestPermissions() casi simultáneo, el segundo se descarta en silencio.
     * Encadenar aquí evita esa carrera.
     *
     * <p>Sin este permiso (Android 13+) FCM sigue entregando data messages, pero el sistema no
     * pinta la notificación — igual registramos el token, la app solo pierde la alerta visual.
     */
    void requestNotificationPermissionAndRegisterToken() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        registerFcmToken();
    }

    private void registerFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            DeviceRepository deviceRepository = new RestDeviceRepository(this);
            deviceRepository.registerDevice(token, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    // no-op
                }

                @Override
                public void onError(ApiException error) {
                    // Se reintenta en el siguiente arranque de HomeActivity.
                }
            });
        });
    }
}
