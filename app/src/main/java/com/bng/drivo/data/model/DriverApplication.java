package com.bng.drivo.data.model;

import java.util.List;

/** GET/POST /driver/application — estado del registro de 7 pasos. */
public class DriverApplication {

    private final String status;
    private final String modality;
    private final List<String> requiredDocuments;
    private final List<String> missingDocuments;
    private final String rejectionReason;
    /** Ver {@link com.bng.drivo.data.remote.dto.ApplicationStatusDto#is_online}. */
    private final boolean online;
    /** Ver {@link com.bng.drivo.data.remote.dto.ApplicationStatusDto#can_go_online}: null = se ignora. */
    private final Boolean canGoOnline;

    public DriverApplication(String status, String modality, List<String> requiredDocuments,
                              List<String> missingDocuments, String rejectionReason, boolean online,
                              Boolean canGoOnline) {
        this.status = status;
        this.modality = modality;
        this.requiredDocuments = requiredDocuments;
        this.missingDocuments = missingDocuments;
        this.rejectionReason = rejectionReason;
        this.online = online;
        this.canGoOnline = canGoOnline;
    }

    /**
     * Si podría conectarse: aprobado y con saldo, según el servidor. <b>Null cuando el servidor no
     * lo dijo</b> — en ese caso no se saca ninguna conclusión, que es distinto de "no puede".
     */
    public Boolean getCanGoOnline() {
        return canGoOnline;
    }

    /**
     * Si está conectado, según el servidor. Es la única verdad: el servidor puede haberlo
     * desconectado por su cuenta (al cerrar un viaje que lo deja bajo el saldo mínimo).
     */
    public boolean isOnline() {
        return online;
    }

    public String getStatus() {
        return status;
    }

    public String getModality() {
        return modality;
    }

    public List<String> getRequiredDocuments() {
        return requiredDocuments;
    }

    public List<String> getMissingDocuments() {
        return missingDocuments;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
