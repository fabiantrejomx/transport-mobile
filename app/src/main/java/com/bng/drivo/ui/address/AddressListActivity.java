package com.bng.drivo.ui.address;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.ui.auth.AuthenticatedActivity;

import com.bng.drivo.R;
import com.bng.drivo.data.model.AddressLabel;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AddressRepository;
import com.bng.drivo.data.repository.RestAddressRepository;
import com.bng.drivo.util.ColorUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class AddressListActivity extends AuthenticatedActivity {

    private AddressRepository addressRepository;
    private LinearLayout container;
    private TextView emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_list);

        addressRepository = new RestAddressRepository(this);
        container = findViewById(R.id.container_addresses);
        emptyState = findViewById(R.id.text_empty);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.row_add_address).setOnClickListener(v ->
                startActivity(new Intent(this, AddEditAddressActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAddresses();
    }

    private void renderAddresses() {
        addressRepository.getAll(new ApiCallback<List<SavedAddress>>() {
            @Override
            public void onSuccess(List<SavedAddress> addresses) {
                bindAddresses(addresses);
            }

            @Override
            public void onError(ApiException error) {
                bindAddresses(java.util.Collections.emptyList());
                Toast.makeText(AddressListActivity.this, R.string.address_list_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindAddresses(List<SavedAddress> addresses) {
        container.removeAllViews();

        container.setVisibility(addresses.isEmpty() ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(addresses.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < addresses.size(); i++) {
            SavedAddress address = addresses.get(i);
            AddressLabel icon = AddressLabel.fromText(this, address.getLabel());
            View row = inflater.inflate(R.layout.item_saved_address, container, false);

            ((TextView) row.findViewById(R.id.text_address_emoji)).setText(icon.getEmoji());
            ((TextView) row.findViewById(R.id.text_address_label)).setText(address.getLabel());
            ((TextView) row.findViewById(R.id.text_address_line)).setText(address.getAddress());

            row.setOnClickListener(v -> openEditor(address));
            // La pulsación larga abre el mismo menú que el botón: es un atajo para quien ya lo
            // sabe, nunca la única forma de llegar — un gesto invisible no se descubre solo.
            row.setOnLongClickListener(v -> {
                showRowMenu(row.findViewById(R.id.btn_address_menu), address);
                return true;
            });
            // Solo aquí: en el modal de Inicio y en el de paradas esta misma fila es un atajo
            // para elegir destino, no el CRUD de direcciones (viene oculto del layout).
            View menuButton = row.findViewById(R.id.btn_address_menu);
            menuButton.setVisibility(View.VISIBLE);
            menuButton.setOnClickListener(v -> showRowMenu(v, address));

            container.addView(row);

            if (i < addresses.size() - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divider.setLayoutParams(params);
                divider.setBackgroundColor(
                        ColorUtils.resolveThemeColor(this, com.google.android.material.R.attr.colorOutline));
                container.addView(divider);
            }
        }
    }

    private void openEditor(SavedAddress address) {
        Intent intent = new Intent(this, AddEditAddressActivity.class);
        intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_ID, address.getId());
        intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_LABEL, address.getLabel());
        intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_TEXT, address.getAddress());
        intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_LAT, address.getLat());
        intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_LNG, address.getLng());
        startActivity(intent);
    }

    /** Ancla el menú al botón de la fila, venga del botón o de la pulsación larga. */
    private void showRowMenu(View anchor, SavedAddress address) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.saved_address_menu);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit_address) {
                openEditor(address);
                return true;
            }
            if (item.getItemId() == R.id.action_delete_address) {
                confirmDelete(address);
                return true;
            }
            return false;
        });
        menu.show();
    }

    /**
     * Borrar no se puede deshacer y el nombre no siempre distingue —dos "Otro" se ven igual en la
     * lista—, así que el diálogo dice cuál se va a ir antes de preguntar.
     */
    private void confirmDelete(SavedAddress address) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.address_list_delete_title)
                .setMessage(getString(R.string.address_list_delete_message_format, address.getLabel()))
                .setPositiveButton(R.string.address_list_delete_positive,
                        (dialog, which) -> deleteAddress(address))
                .setNegativeButton(R.string.address_list_delete_negative, null)
                .show();
    }

    private void deleteAddress(SavedAddress address) {
        addressRepository.delete(address.getId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(AddressListActivity.this, R.string.address_list_deleted_toast,
                        Toast.LENGTH_SHORT).show();
                renderAddresses();
            }

            @Override
            public void onError(ApiException error) {
                Toast.makeText(AddressListActivity.this, R.string.address_edit_delete_error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
