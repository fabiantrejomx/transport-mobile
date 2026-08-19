package com.bng.drivo.ui.settings;

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

/** Modal genérico de una sola línea para ajustes sin backend real todavía (métodos de pago,
 * notificaciones) — mismo patrón "próximamente" ya usado en el resto de la app. */
public class SimpleMessageBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_MESSAGE = "arg_message";

    public static void present(FragmentManager fragmentManager, String title, String message) {
        SimpleMessageBottomSheet sheet = new SimpleMessageBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        sheet.setArguments(args);
        sheet.show(fragmentManager, "simple_message");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_simple_message, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        ((TextView) view.findViewById(R.id.text_message_title)).setText(args.getString(ARG_TITLE));
        ((TextView) view.findViewById(R.id.text_message_body)).setText(args.getString(ARG_MESSAGE));
    }
}
