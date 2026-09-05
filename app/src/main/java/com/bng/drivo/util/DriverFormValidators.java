package com.bng.drivo.util;

import androidx.annotation.Nullable;

import java.time.Year;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CURP, RFC, placa y año del vehículo del registro de conductor.
 *
 * <p>Es el espejo de {@code mx.librego.api.domain.DriverIdentifiers} en transport-api. Las reglas
 * están duplicadas a propósito: el servidor es el que manda —el teléfono no es de fiar— pero él
 * solo puede contestar "el CURP está mal" cuando el conductor ya llenó los 7 pasos y subió once
 * fotos. Validar aquí es lo que permite marcar el campo exacto mientras todavía lo tiene enfrente.
 *
 * <p>Cada método <b>normaliza y valida en el mismo paso</b>: quien captura escribe en minúsculas y
 * pone guiones en la placa, y eso son capturas correctas, no errores. Lo que se envía es la forma
 * normalizada, la misma que el servidor guardaría de todos modos.
 */
public final class DriverFormValidators {

    /**
     * CURP de RENAPO: inicial + vocal + dos consonantes, fecha de nacimiento, sexo, entidad,
     * tres consonantes internas, homoclave y dígito verificador.
     *
     * <p>Las 32 entidades van enumeradas (más {@code NE}, nacido en el extranjero) porque son un
     * catálogo cerrado; con {@code [A-Z]{2}} se colaría la mitad de los errores de dedo.
     *
     * <p>El dígito verificador no se comprueba: circulan CURP antiguos cuyo dígito no cuadra con el
     * algoritmo actual, y rechazar a alguien por el documento que RENAPO le dio sería peor que
     * dejar pasar una errata que la revisión manual ve igual.
     */
    private static final Pattern CURP = Pattern.compile(
            "^[A-Z][AEIOUX][A-Z]{2}"
                    + "\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])"
                    + "[HM]"
                    + "(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL"
                    + "|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)"
                    + "[B-DF-HJ-NP-TV-Z]{3}"
                    + "[A-Z\\d]\\d$");

    /**
     * RFC del SAT: 13 para persona física (cuatro letras y fecha), 12 para moral (tres y fecha),
     * más la homoclave de tres.
     *
     * <p>Se aceptan las dos longitudes aunque quien se registra sea una persona: hay concesionarios
     * que operan el taxi a nombre de su empresa y facturan con ese RFC.
     */
    private static final Pattern RFC = Pattern.compile(
            "^[A-ZÑ&]{3,4}"
                    + "\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])"
                    + "[A-Z\\d]{3}$");

    /**
     * Placa ya sin guiones ni espacios.
     *
     * <p>Se valida <b>longitud y alfabeto</b>, no un formato exacto: cada estado emite el suyo y
     * conviven varios vigentes —{@code ABC-123-D} de particular, {@code YKV-889} de servicio
     * público, los {@code ABC-1234} anteriores—, así que un patrón cerrado rechazaría placas
     * reales. De 6 a 8 caracteres atrapa lo que importa: el campo a medio llenar y el dedazo.
     */
    private static final Pattern PLATE = Pattern.compile("^[A-Z0-9]{6,8}$");

    /**
     * Antigüedad máxima del vehículo, en años.
     *
     * <p>La ventana <b>se mueve con el calendario</b>: en 2026 se admite del 2016 al 2027. Es una
     * regla de flota, no una fecha, así que un año fijo se volvería más permisivo cada enero sin
     * que nadie lo decidiera.
     */
    public static final int MAX_VEHICLE_AGE_YEARS = 10;

    private DriverFormValidators() {
    }

    /** Mayúsculas y sin espacios. No dice si es válido: eso lo contesta {@link #isValidCurp}. */
    public static String normalizeCurp(@Nullable String raw) {
        return compact(raw);
    }

    public static boolean isValidCurp(String normalized) {
        return CURP.matcher(normalized).matches();
    }

    /** Mayúsculas, sin espacios ni guiones. */
    public static String normalizeRfc(@Nullable String raw) {
        return compact(raw).replace("-", "");
    }

    public static boolean isValidRfc(String normalized) {
        return RFC.matcher(normalized).matches();
    }

    /** Mayúsculas y solo caracteres alfanuméricos: la placa se guarda sin separadores. */
    public static String normalizePlate(@Nullable String raw) {
        return compact(raw).replaceAll("[^A-Z0-9]", "");
    }

    public static boolean isValidPlate(String normalized) {
        return PLATE.matcher(normalized).matches();
    }

    /**
     * El año más nuevo que se puede registrar: el que entra.
     *
     * <p>Los modelos salen a la venta antes que su año, así que un conductor puede tener hoy,
     * legítimamente, un vehículo del año siguiente.
     */
    public static int maxVehicleYear() {
        return Year.now().getValue() + 1;
    }

    /** El modelo más viejo admitido: diez años atrás. */
    public static int minVehicleYear() {
        return Year.now().getValue() - MAX_VEHICLE_AGE_YEARS;
    }

    public static boolean isValidVehicleYear(int year) {
        return year >= minVehicleYear() && year <= maxVehicleYear();
    }

    private static String compact(@Nullable String raw) {
        return raw == null ? "" : raw.trim().replace(" ", "").toUpperCase(Locale.ROOT);
    }
}
