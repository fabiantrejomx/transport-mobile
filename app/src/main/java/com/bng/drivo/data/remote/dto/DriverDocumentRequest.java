package com.bng.drivo.data.remote.dto;

public class DriverDocumentRequest {
    public String type;
    public String storage_path;

    public DriverDocumentRequest(String type, String storagePath) {
        this.type = type;
        this.storage_path = storagePath;
    }
}
