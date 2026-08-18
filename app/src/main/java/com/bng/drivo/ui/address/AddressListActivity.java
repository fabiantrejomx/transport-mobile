package com.bng.drivo.ui.address;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
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

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, AddEditAddressActivity.class);
                intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_ID, address.getId());
                intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_LABEL, address.getLabel());
                intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_TEXT, address.getAddress());
                intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_LAT, address.getLat());
                intent.putExtra(AddEditAddressActivity.EXTRA_ADDRESS_LNG, address.getLng());
                startActivity(intent);
            });

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
}
