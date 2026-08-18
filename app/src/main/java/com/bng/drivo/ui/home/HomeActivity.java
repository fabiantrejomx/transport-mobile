package com.bng.drivo.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.bng.drivo.ui.auth.AuthenticatedActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.bng.drivo.R;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DeviceRepository;
import com.bng.drivo.data.repository.RestDeviceRepository;
import com.bng.drivo.ui.profile.PerfilFragment;
import com.bng.drivo.ui.trips.ViajesFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Host de las 3 pestañas del pasajero (Inicio / Viajes / Perfil), con la barra de
 * navegación inferior siempre visible — patrón tipo WhatsApp: cambiar de pestaña solo
 * intercambia el contenido del contenedor, nunca oculta la barra ni apila una Activity nueva.
 */
public class HomeActivity extends AuthenticatedActivity {

    private static final String TAG_HOME = "tab_home";
    private static final String TAG_VIAJES = "tab_viajes";
    private static final String TAG_PERFIL = "tab_perfil";

    private Fragment homeFragment;
    private Fragment viajesFragment;
    private Fragment perfilFragment;
    private Fragment activeFragment;

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
            perfilFragment = fragmentManager.findFragmentByTag(TAG_PERFIL);
        }
        if (homeFragment == null) {
            homeFragment = new HomeFragment();
        }
        if (viajesFragment == null) {
            viajesFragment = new ViajesFragment();
        }
        if (perfilFragment == null) {
            perfilFragment = new PerfilFragment();
        }

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        addIfNeeded(transaction, fragmentManager, homeFragment, TAG_HOME);
        addIfNeeded(transaction, fragmentManager, viajesFragment, TAG_VIAJES);
        addIfNeeded(transaction, fragmentManager, perfilFragment, TAG_PERFIL);
        transaction.show(homeFragment).hide(viajesFragment).hide(perfilFragment);
        transaction.commitNow();
        activeFragment = homeFragment;

        setUpBottomNav();
    }

    private void addIfNeeded(FragmentTransaction transaction, FragmentManager fragmentManager,
                              Fragment fragment, String tag) {
        if (fragmentManager.findFragmentByTag(tag) == null) {
            transaction.add(R.id.fragment_container, fragment, tag);
        }
    }

    private void setUpBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_inicio);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inicio) {
                showTab(homeFragment);
                return true;
            }
            if (id == R.id.nav_viajes) {
                showTab(viajesFragment);
                return true;
            }
            if (id == R.id.nav_perfil) {
                showTab(perfilFragment);
                return true;
            }
            return false;
        });
    }

    private void showTab(Fragment target) {
        if (target == activeFragment) {
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
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
