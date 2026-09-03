package com.bng.drivo.data.remote.dto;

public class MeDto {
    public String id;

    /** Cadena vacía en una cuenta de Google que todavía no ha escrito su número. */
    public String phone;
    public String name;
    public String email;
    public String photo_url;

    /**
     * Cómo se dio de alta la cuenta: {@code "phone"} o {@code "google"}. Decide qué campo del
     * perfil se puede editar — el que verificó el proveedor queda fijo.
     *
     * <p>La app lo usa solo para <b>dibujar</b> el candado. Quien lo aplica de verdad es
     * {@code PATCH /me}, que responde 403 si llega el campo bloqueado: deshabilitar un EditText no
     * detiene a nadie, y en el caso del teléfono ese candado protege la identidad con la que el
     * conductor llama al pasajero.
     */
    public String auth_provider;

    /**
     * Promedio de estrellas del propio usuario, para la pastilla de la cabecera del cajón.
     *
     * <p><b>Todavía no está en el contrato v1.3.0</b>, así que hoy llega null y la pastilla no se
     * pinta. Se declara ya porque es el único sitio del que puede salir sin coste: las dos apps
     * llaman a /me para el nombre, y el backend ya calcula este número —es el mismo que sirve en
     * {@code DriverSummary.rating} y {@code PassengerSummary.rating}—, así que en cuanto lo exponga
     * aquí las dos cabeceras se encienden solas sin tocar el cliente.
     *
     * <p>Para el pasajero es además la <b>única</b> vía posible: ninguna ruta que él tenga permiso
     * de llamar devuelve su propia calificación.
     */
    public Double rating;

    /**
     * Viajes cerrados que cuentan para el promedio. Es lo que distingue un 5.0 real de un 5.0 por
     * omisión: con {@code trips == 0} la pastilla dice "Nuevo" en vez de un número que nadie ha
     * ganado todavía. El contrato ya empareja así los dos campos en {@code PassengerSummary}.
     */
    public Integer trips;
}
