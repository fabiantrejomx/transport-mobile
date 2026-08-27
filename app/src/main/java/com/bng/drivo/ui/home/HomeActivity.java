package com.bng.drivo.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.bng.drivo.ui.auth.AuthenticatedActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.bng.drivo.R;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.model.UserProfile;
import com.bng.drivo.data.repository.DeviceRepository;
import com.bng.drivo.data.repository.RestDeviceRepository;
import com.bng.drivo.data.repository.RestUserRepository;
import com.bng.drivo.data.repository.UserRepository;
import com.bng.drivo.ui.settings.ConfiguracionesFragment;
import com.bng.drivo.util.DrawerInsets;
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
    private View navHeader;
    private int navHeaderBasePaddingPx;
    private OnBackPressedCallback backToHomeCallback;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> registerFcmToken());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Antes de añadir los fragments: commitNow() ejecuta onViewCreated en el acto, y
        // HomeFragment ya llama ahí a setDrawerEnabled() al pintar su paso inicial.
        setUpDrawer();
        setUpBackToHome();

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
    }

    /**
     * Desde Viajes o Configuración, "atrás" vuelve a Inicio en vez de cerrar la app. Las tres
     * secciones son fragments de esta misma Activity, así que sin esto el gesto llegaba al
     * sistema y la app se cerraba desde una pantalla que, para el usuario, tiene un "arriba"
     * evidente.
     *
     * <p>Se registra antes que el callback de HomeFragment a propósito: el despachador consulta
     * primero al último registrado, así que el flujo del viaje (que necesita el gesto para volver
     * de un paso a otro) siempre gana cuando está activo, y este solo entra cuando aquel está
     * apagado.
     */
    private void setUpBackToHome() {
        backToHomeCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                navView.setCheckedItem(R.id.nav_inicio);
                showTab(homeFragment);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backToHomeCallback);
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
        setUpDrawerHeader();
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

    /**
     * Identidad del pasajero arriba del menú: es el sitio donde uno espera encontrarse a sí mismo
     * en cualquier app con cajón, y de paso confirma con qué cuenta está entrando. Tocarla lleva
     * a Configuración, que es donde se edita el perfil.
     */
    private void setUpDrawerHeader() {
        navHeader = navView.getHeaderView(0);
        navHeaderBasePaddingPx = navHeader.getPaddingTop();
        navHeader.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            navView.setCheckedItem(R.id.nav_configuraciones);
            showTab(configuracionesFragment);
        });
        // NavigationView deja de compensar la status bar en cuanto hay cabecera — ver DrawerInsets.
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                DrawerInsets.applyTopInset(navHeader, navHeaderBasePaddingPx);
            }
        });
    }

    private void loadDrawerProfile() {
        UserRepository userRepository = new RestUserRepository(this);
        userRepository.getCurrentUser(new ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                ((TextView) navHeader.findViewById(R.id.text_nav_avatar)).setText(profile.getInitials());
                ((TextView) navHeader.findViewById(R.id.text_nav_name)).setText(profile.getName());
                ((TextView) navHeader.findViewById(R.id.text_nav_phone)).setText(profile.getPhone());
            }

            @Override
            public void onError(ApiException error) {
                // La cabecera es identidad, no funcionalidad: sin datos se queda vacía y el menú
                // sigue sirviendo igual.
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // El nombre se puede editar desde Configuración, en esta misma pantalla.
        loadDrawerProfile();
    }

    /**
     * Bloquea el menú lateral mientras el pasajero está a mitad de una solicitud (ver los pasos
     * de HomeFragment). No basta con cambiar el botón flotante a "atrás": el cajón también se
     * abre deslizando desde el borde, y ese gesto sacaría del flujo a quien solo quería mover el
     * mapa.
     */
    public void setDrawerEnabled(boolean enabled) {
        drawerLayout.setDrawerLockMode(enabled
                ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
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
        // Solo hay a dónde volver si no estamos ya en Inicio.
        backToHomeCallback.setEnabled(activeFragment != homeFragment);
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
