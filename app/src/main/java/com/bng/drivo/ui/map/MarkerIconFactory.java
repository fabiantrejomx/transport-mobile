package com.bng.drivo.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Iconos de marcador dibujados a mano (sin depender de assets externos): círculos
 * minimalistas de marca para puntos fijos (origen/destino) y un indicativo tipo "coche"
 * para el conductor, que se reorienta según el rumbo de avance — ver ActiveTripActivity.
 */
public final class MarkerIconFactory {

    private MarkerIconFactory() {
    }

    /** Círculo sólido con borde blanco, en el color de marca indicado. */
    public static BitmapDescriptor circle(Context context, @ColorRes int colorRes, int diameterDp) {
        float density = context.getResources().getDisplayMetrics().density;
        int diameterPx = Math.round(diameterDp * density);
        float strokeWidthPx = 2.5f * density;

        Bitmap bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float cx = diameterPx / 2f;
        float cy = diameterPx / 2f;
        float radius = diameterPx / 2f - strokeWidthPx;

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(ContextCompat.getColor(context, colorRes));
        fillPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius, fillPaint);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidthPx);
        canvas.drawCircle(cx, cy, radius, strokePaint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * Indicativo minimalista de vehículo visto desde arriba, apuntando al norte (0°) por
     * defecto — combínalo con {@code Marker#setRotation} y {@code setFlat(true)} para que
     * gire según el rumbo real de avance, como en Uber/Didi/InDrive.
     */
    public static BitmapDescriptor carMarker(Context context, @ColorRes int colorRes) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(36 * density);

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(50, 0, 0, 0));
        canvas.drawOval(size * 0.24f, size * 0.64f, size * 0.76f, size * 0.86f, shadowPaint);

        Path body = new Path();
        float cx = size / 2f;
        // Cuerpo tipo "gota" apuntando hacia arriba: la punta superior es el frente del coche.
        body.moveTo(cx, size * 0.06f);
        body.cubicTo(size * 0.86f, size * 0.26f, size * 0.82f, size * 0.55f, size * 0.76f, size * 0.7f);
        body.cubicTo(size * 0.7f, size * 0.82f, size * 0.58f, size * 0.88f, cx, size * 0.88f);
        body.cubicTo(size * 0.42f, size * 0.88f, size * 0.3f, size * 0.82f, size * 0.24f, size * 0.7f);
        body.cubicTo(size * 0.18f, size * 0.55f, size * 0.14f, size * 0.26f, cx, size * 0.06f);
        body.close();

        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(ContextCompat.getColor(context, colorRes));
        bodyPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(body, bodyPaint);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f * density);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(body, strokePaint);

        Paint windshieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        windshieldPaint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawOval(size * 0.36f, size * 0.2f, size * 0.64f, size * 0.4f, windshieldPaint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
}
