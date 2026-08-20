package com.bng.drivo.ui.driver;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.bng.drivo.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Menú del hamburger de DriverHomeActivity — atajo a las 3 pantallas fuera del radar. */
public class DriverMenuBottomSheet extends BottomSheetDialogFragment {

    public static void present(FragmentManager fragmentManager) {
        new DriverMenuBottomSheet().show(fragmentManager, "driver_menu");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_driver_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindRow(view, R.id.row_menu_earnings, "📈", R.string.driver_home_menu_earnings, DriverEarningsActivity.class);
        bindRow(view, R.id.row_menu_settings, "⚙️", R.string.driver_home_menu_settings, DriverSettingsActivity.class);
        bindRow(view, R.id.row_menu_security, "🛡️", R.string.driver_home_menu_security, DriverSecurityActivity.class);
    }

    private void bindRow(View parent, int includeId, String icon, int labelRes, Class<?> destination) {
        View row = parent.findViewById(includeId);
        ((TextView) row.findViewById(R.id.row_icon)).setText(icon);
        ((TextView) row.findViewById(R.id.row_label)).setText(labelRes);
        // "row" ya es la raíz clicable del <include> (su propio id la sobrescribe a la del
        // include, "row_content" deja de existir dentro del árbol) — no volver a buscarla.
        row.setOnClickListener(v -> {
            dismiss();
            startActivity(new Intent(requireContext(), destination));
        });
    }
}
