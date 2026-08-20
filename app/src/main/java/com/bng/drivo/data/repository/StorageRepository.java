package com.bng.drivo.data.repository;

import java.io.File;

/** Subida directa a Cloud Storage — las fotos de documentos nunca pasan por transport-api. */
public interface StorageRepository {

    interface UploadCallback {
        void onSuccess(String storagePath);

        void onError(Exception error);
    }

    /** Sube {@code file} a {@code drivers/{uid}/{type}.jpg} y devuelve la ruta lista para POST /driver/documents. */
    void uploadDriverDocument(String uid, String type, File file, UploadCallback callback);
}
