package com.bng.drivo.ui.home;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.bng.drivo.R;
import com.bng.drivo.ui.profile.PerfilFragment;
import com.bng.drivo.ui.trips.ViajesFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Host de las 3 pestañas del pasajero (Inicio / Viajes / Perfil), con la barra de
 * navegación inferior siempre visible — patrón tipo WhatsApp: cambiar de pestaña solo
 * intercambia el contenido del contenedor, nunca oculta la barra ni apila una Activity nueva.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG_HOME = "tab_home";
    private static final String TAG_VIAJES = "tab_viajes";
    private static final String TAG_PERFIL = "tab_perfil";

    private Fragment homeFragment;
    private Fragment viajesFragment;
    private Fragment perfilFragment;
    private Fragment activeFragment;

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
}
