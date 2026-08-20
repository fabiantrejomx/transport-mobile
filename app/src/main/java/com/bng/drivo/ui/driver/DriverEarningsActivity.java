package com.bng.drivo.ui.driver;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bng.drivo.R;
import com.bng.drivo.data.model.Wallet;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.util.RelativeDateFormatter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * C5: ganancias del conductor a partir de GET /driver/wallet, el único origen real de datos
 * de dinero. El wallet solo registra lo que se descuenta (comisión/ajustes/impuestos) — el
 * efectivo del viaje nunca lo toca la API — así que "ganado hoy" del mockup ($840, con nombre
 * de pasajero por viaje) no es reconstruible sin inventar datos. En su lugar: saldo real +
 * conteo real de viajes de hoy (una fila `commission` = un viaje cerrado) + el libro contable
 * completo, sin nombres de pasajero (ese dato no viene en /driver/wallet).
 */
public class DriverEarningsActivity extends AuthenticatedActivity {

    private DriverRepository driverRepository;

    private TextView textBalance;
    private TextView textTripsToday;
    private LinearLayout containerEntries;
    private View textEmpty;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_earnings);

        driverRepository = new RestDriverRepository(this);

        textBalance = findViewById(R.id.text_earnings_balance);
        textTripsToday = findViewById(R.id.text_earnings_trips_today);
        containerEntries = findViewById(R.id.container_entries);
        textEmpty = findViewById(R.id.text_earnings_empty);
        progress = findViewById(R.id.progress_earnings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadWallet();
    }

    private void loadWallet() {
        progress.setVisibility(View.VISIBLE);
        driverRepository.getWallet(new ApiCallback<Wallet>() {
            @Override
            public void onSuccess(Wallet wallet) {
                progress.setVisibility(View.GONE);
                bindWallet(wallet);
            }

            @Override
            public void onError(ApiException error) {
                progress.setVisibility(View.GONE);
                Toast.makeText(DriverEarningsActivity.this, R.string.driver_earnings_load_error, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void bindWallet(Wallet wallet) {
        textBalance.setText(String.format(Locale.getDefault(), "$ %.2f", wallet.getBalance()));

        List<Wallet.WalletEntry> entries = wallet.getEntries();
        LocalDate today = LocalDate.now();
        int tripsToday = 0;

        containerEntries.removeAllViews();
        for (Wallet.WalletEntry entry : entries) {
            if ("commission".equals(entry.getType()) && isToday(entry.getCreatedAt(), today)) {
                tripsToday++;
            }
            containerEntries.addView(buildEntryRow(entry));
        }

        textTripsToday.setText(getString(R.string.driver_earnings_trips_today_format, tripsToday));
        textEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean isToday(String isoTimestamp, LocalDate today) {
        if (isoTimestamp == null) {
            return false;
        }
        try {
            return Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).toLocalDate().equals(today);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private View buildEntryRow(Wallet.WalletEntry entry) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_wallet_entry, containerEntries, false);

        ((TextView) row.findViewById(R.id.text_entry_type)).setText(labelFor(entry.getType()));

        String date = RelativeDateFormatter.format(entry.getCreatedAt());
        String note = entry.getNote();
        String detail = note != null && !note.isEmpty() ? date + " · " + note : date;
        ((TextView) row.findViewById(R.id.text_entry_detail)).setText(detail);

        TextView textAmount = row.findViewById(R.id.text_entry_amount);
        String sign = entry.getAmount() >= 0 ? "+ " : "- ";
        textAmount.setText(String.format(Locale.getDefault(), "%s$%.2f", sign, Math.abs(entry.getAmount())));
        textAmount.setTextColor(getColor(entry.getAmount() >= 0 ? R.color.drivo_success : R.color.drivo_error));

        return row;
    }

    private String labelFor(String type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case "commission":
                return getString(R.string.driver_earnings_type_commission);
            case "recharge":
                return getString(R.string.driver_earnings_type_recharge);
            case "adjustment":
                return getString(R.string.driver_earnings_type_adjustment);
            case "isr":
                return getString(R.string.driver_earnings_type_isr);
            case "iva":
                return getString(R.string.driver_earnings_type_iva);
            default:
                return type;
        }
    }
}
