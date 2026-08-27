package com.bng.drivo.util;

import android.content.Context;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Copia local de los datos que el conductor mandó en su registro (CURP, RFC y vehículo).
 *
 * <p>Existe porque el contrato no los devuelve: GET /driver/application solo trae estado,
 * modalidad y el detalle de documentos — nunca lo que se capturó en el formulario. Sin esto, la
 * pantalla de configuración no tenía forma de contestar "¿qué fue exactamente lo que envié?", y
 * "Mi Vehículo" era un aviso de "próximamente" sobre datos que el propio conductor acababa de
 * escribir.
 *
 * <p>Es una caché, no la fuente de verdad: se llena al enviar el registro desde este teléfono, así
 * que puede no existir (reinstalación, otro dispositivo). Quien la lee tiene que contemplar el
 * null — ver DriverSettingsActivity.
 */
public final class SubmittedApplicationCache {

    private static final String PREF_KEY = "driver_submitted_application";

    private static final String KEY_MODALITY = "modality";
    private static final String KEY_CURP = "curp";
    private static final String KEY_RFC = "rfc";
    private static final String KEY_BRAND = "brand";
    private static final String KEY_MODEL = "model";
    private static final String KEY_COLOR = "color";
    private static final String KEY_PLATE = "plate";
    private static final String KEY_YEAR = "year";
    private static final String KEY_IS_OWNER = "is_owner";

    private SubmittedApplicationCache() {
    }

    public static void save(Context context, String modality, String curp, @Nullable String rfc,
                             String brand, String model, String color, String plate, int year,
                             boolean isOwner) {
        JSONObject json = new JSONObject();
        try {
            json.put(KEY_MODALITY, modality);
            json.put(KEY_CURP, curp);
            json.put(KEY_RFC, rfc != null ? rfc : "");
            json.put(KEY_BRAND, brand);
            json.put(KEY_MODEL, model);
            json.put(KEY_COLOR, color);
            json.put(KEY_PLATE, plate);
            json.put(KEY_YEAR, year);
            json.put(KEY_IS_OWNER, isOwner);
        } catch (JSONException e) {
            return;
        }
        new PrefsHelper(context).putString(PREF_KEY, json.toString());
    }

    /** @return null si este teléfono no fue el que envió el registro. */
    @Nullable
    public static Submitted read(Context context) {
        String raw = new PrefsHelper(context).getString(PREF_KEY, null);
        if (raw == null) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(raw);
            return new Submitted(
                    json.optString(KEY_MODALITY),
                    json.optString(KEY_CURP),
                    json.optString(KEY_RFC),
                    json.optString(KEY_BRAND),
                    json.optString(KEY_MODEL),
                    json.optString(KEY_COLOR),
                    json.optString(KEY_PLATE),
                    json.optInt(KEY_YEAR),
                    json.optBoolean(KEY_IS_OWNER));
        } catch (JSONException e) {
            return null;
        }
    }

    public static final class Submitted {
        public final String modality;
        public final String curp;
        public final String rfc;
        public final String brand;
        public final String model;
        public final String color;
        public final String plate;
        public final int year;
        public final boolean isOwner;

        Submitted(String modality, String curp, String rfc, String brand, String model, String color,
                   String plate, int year, boolean isOwner) {
            this.modality = modality;
            this.curp = curp;
            this.rfc = rfc;
            this.brand = brand;
            this.model = model;
            this.color = color;
            this.plate = plate;
            this.year = year;
            this.isOwner = isOwner;
        }
    }
}
