package com.bng.drivo.data.repository;

import android.content.Context;

import com.bng.drivo.R;
import com.bng.drivo.data.model.AddressLabel;
import com.bng.drivo.data.model.SavedAddress;
import com.bng.drivo.util.PrefsHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MockAddressRepository implements AddressRepository {

    private static final String KEY_ADDRESSES = "saved_addresses";

    private static final double SEED_HOME_LAT = 19.4285;
    private static final double SEED_HOME_LNG = -99.1277;
    private static final double SEED_WORK_LAT = 19.4358;
    private static final double SEED_WORK_LNG = -99.1402;

    private final Context context;
    private final PrefsHelper prefsHelper;

    public MockAddressRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefsHelper = new PrefsHelper(context);
    }

    @Override
    public List<SavedAddress> getAll() {
        JSONArray array = prefsHelper.getJsonArray(KEY_ADDRESSES);
        if (array == null) {
            List<SavedAddress> seed = createSeedAddresses();
            writeAll(seed);
            return seed;
        }
        return parse(array);
    }

    @Override
    public void save(SavedAddress address) {
        List<SavedAddress> current = getAll();
        boolean replaced = false;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getId().equals(address.getId())) {
                current.set(i, address);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            current.add(address);
        }
        writeAll(current);
    }

    @Override
    public void delete(String id) {
        List<SavedAddress> current = getAll();
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getId().equals(id)) {
                current.remove(i);
                break;
            }
        }
        writeAll(current);
    }

    private List<SavedAddress> createSeedAddresses() {
        List<SavedAddress> seed = new ArrayList<>();
        seed.add(new SavedAddress(AddressLabel.CASA,
                context.getString(R.string.set_destino_home_address), SEED_HOME_LAT, SEED_HOME_LNG));
        seed.add(new SavedAddress(AddressLabel.TRABAJO,
                context.getString(R.string.set_destino_work_address), SEED_WORK_LAT, SEED_WORK_LNG));
        return seed;
    }

    private void writeAll(List<SavedAddress> addresses) {
        JSONArray array = new JSONArray();
        for (SavedAddress address : addresses) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", address.getId());
                obj.put("label", address.getLabel().name());
                obj.put("address", address.getAddress());
                obj.put("lat", address.getLat());
                obj.put("lng", address.getLng());
            } catch (JSONException ignored) {
                continue;
            }
            array.put(obj);
        }
        prefsHelper.putJsonArray(KEY_ADDRESSES, array);
    }

    private List<SavedAddress> parse(JSONArray array) {
        List<SavedAddress> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            String id = obj.optString("id");
            AddressLabel label;
            try {
                label = AddressLabel.valueOf(obj.optString("label", AddressLabel.OTRO.name()));
            } catch (IllegalArgumentException e) {
                label = AddressLabel.OTRO;
            }
            result.add(new SavedAddress(id, label, obj.optString("address"),
                    obj.optDouble("lat", 0), obj.optDouble("lng", 0)));
        }
        return result;
    }
}
