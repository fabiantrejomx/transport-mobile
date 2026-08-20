package com.bng.drivo.data.repository;

import android.net.Uri;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;

public class FirebaseStorageRepository implements StorageRepository {

    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    @Override
    public void uploadDriverDocument(String uid, String type, File file, UploadCallback callback) {
        String path = "drivers/" + uid + "/" + type + ".jpg";
        StorageReference reference = storage.getReference().child(path);
        reference.putFile(Uri.fromFile(file))
                .addOnSuccessListener(taskSnapshot -> callback.onSuccess(path))
                .addOnFailureListener(callback::onError);
    }
}
