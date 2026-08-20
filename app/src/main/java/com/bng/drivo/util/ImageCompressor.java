package com.bng.drivo.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Comprime una foto a ~1600px de lado mayor / JPEG 80 antes de subirla a Cloud Storage — el
 * contrato lo pide explícito: "la diferencia entre una subida de 2 segundos y una de 30 en la
 * red de un taxista". Se corre en background (ver DriverRegistrationActivity), nunca en el
 * hilo principal.
 */
public final class ImageCompressor {

    private static final int MAX_DIMENSION = 1600;
    private static final int JPEG_QUALITY = 80;

    private ImageCompressor() {
    }

    public static File compress(Context context, Uri sourceUri, File outputFile) throws IOException {
        Bitmap bitmap = decodeSampled(context, sourceUri, MAX_DIMENSION);
        Bitmap scaled = scaleToMax(bitmap, MAX_DIMENSION);
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        }
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        scaled.recycle();
        return outputFile;
    }

    private static Bitmap decodeSampled(Context context, Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream boundsStream = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        }

        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= maxDimension || bounds.outHeight / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions);
            if (bitmap == null) {
                throw new IOException("No se pudo decodificar la imagen: " + uri);
            }
            return bitmap;
        }
    }

    private static Bitmap scaleToMax(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float largestSide = Math.max(width, height);
        if (largestSide <= maxDimension) {
            return bitmap;
        }
        float scale = maxDimension / largestSide;
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }
}
