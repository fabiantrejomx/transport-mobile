package com.bng.drivo.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.bng.drivo.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Las salidas del lado conductor cuando no se puede avanzar: el registro sin terminar y el
 * expediente todavía sin aprobar.
 *
 * <p>Un solo modal para las dos pantallas porque el problema es el mismo, y porque así la salida
 * está siempre en el mismo sitio: quien la buscó una vez en el registro la encuentra igual días
 * después en la tarjeta de "en revisión".
 *
 * <p>Cerrar sesión pide confirmación y cambiar a pasajero no: lo primero obliga a volver a recibir
 * un SMS para entrar, lo segundo es un cambio de pantalla que se deshace desde Configuración. Un
 * diálogo delante de algo reversible solo enseña a descartar diálogos.
 */
public class SessionExitBottomSheet extends BottomSheetDialogFragment {

    public static void present(@NonNull FragmentActivity activity) {
        new SessionExitBottomSheet().show(activity.getSupportFragmentManager(), "session_exit");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_session_exit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.row_session_to_passenger).setOnClickListener(v -> {
            dismiss();
            SessionExit.toPassenger(requireActivity());
        });

        view.findViewById(R.id.row_session_logout).setOnClickListener(v -> confirmLogout());
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.session_exit_logout_confirm_title)
                .setMessage(R.string.session_exit_logout_confirm_message)
                .setPositiveButton(R.string.perfil_logout, (dialog, which) -> {
                    dismiss();
                    SessionExit.logout(requireActivity());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
