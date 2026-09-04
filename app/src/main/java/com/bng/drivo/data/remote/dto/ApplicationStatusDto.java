package com.bng.drivo.data.remote.dto;

import java.util.List;

public class ApplicationStatusDto {
    public String status;
    public String modality;
    public List<String> required_documents;
    public List<String> missing_documents;
    public String rejection_reason;
    /**
     * Si el conductor está conectado ahora mismo (contrato 1.10.0). El servidor es el único que lo
     * sabe: el estado vive en su base y sobrevive a que la app se cierre. Contra un servidor
     * anterior llega ausente y Gson lo deja en false, que es el estado seguro — el conductor lo ve
     * desconectado y puede volver a conectarse.
     */
    public boolean is_online;
    /**
     * Si <b>podría</b> conectarse ahora mismo: aprobado y con saldo (contrato 1.11.0).
     *
     * <p>Es {@code Boolean} y no {@code boolean} a propósito. Con el primitivo, un servidor que
     * todavía no mande el campo lo dejaría en {@code false} y la app le diría "sin saldo" a un
     * conductor que lo tiene. Nulo significa "no lo sé", y entonces no se afirma nada.
     */
    public Boolean can_go_online;
}
