package com.bng.drivo.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.bng.drivo.R;
import com.bng.drivo.util.ThemePreferences;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Sistema / Claro / Oscuro — reemplaza el AlertDialog anterior por un modal tipo card, misma
 * lógica de ThemePreferences (AppCompatDelegate.setDefaultNightMode). */
public class AppearanceBottomSheet extends BottomSheetDialogFragment {

    public static void present(FragmentManager fragmentManager) {
        new AppearanceBottomSheet().show(fragmentManager, "appearance");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_appearance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout container = view.findViewById(R.id.container_options);
        ThemePreferences.Mode[] modes = ThemePreferences.Mode.values();
        int[] labels = {R.string.perfil_appearance_system, R.string.perfil_appearance_light, R.string.perfil_appearance_dark};
        ThemePreferences.Mode current = ThemePreferences.getMode(requireContext());

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < modes.length; i++) {
            ThemePreferences.Mode mode = modes[i];
            View row = inflater.inflate(R.layout.item_appearance_option, container, false);
            ((TextView) row.findViewById(R.id.text_option_label)).setText(labels[i]);
            RadioButton radio = row.findViewById(R.id.radio_option);
            radio.setChecked(mode == current);
            row.setOnClickListener(v -> {
                ThemePreferences.setMode(requireContext(), mode);
                dismiss();
            });
            container.addView(row);
        }
    }
}
