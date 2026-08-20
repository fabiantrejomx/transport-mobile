package com.bng.drivo.data.model;

import java.util.List;

/** GET/POST /driver/application — estado del registro de 7 pasos. */
public class DriverApplication {

    private final String status;
    private final String modality;
    private final List<String> requiredDocuments;
    private final List<String> missingDocuments;
    private final String rejectionReason;

    public DriverApplication(String status, String modality, List<String> requiredDocuments,
                              List<String> missingDocuments, String rejectionReason) {
        this.status = status;
        this.modality = modality;
        this.requiredDocuments = requiredDocuments;
        this.missingDocuments = missingDocuments;
        this.rejectionReason = rejectionReason;
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
