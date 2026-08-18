package com.bng.drivo.data.remote.dto;

import java.util.List;

public class ApplicationStatusDto {
    public String status;
    public String modality;
    public List<String> required_documents;
    public List<String> missing_documents;
    public String rejection_reason;
}
