package com.bng.drivo.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.bng.drivo.R;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Iconos de marcador dibujados a mano (sin depender de assets externos): círculos
 * minimalistas de marca para puntos fijos (origen/destino) y un sprite tipo "coche" visto
 * desde arriba para el conductor, que se reorienta según el rumbo de avance — ver
 * ActiveTripActivity. El coche es un compuesto de 3 vectores en capas (sombra, carrocería,
 * detalle) porque un tinte de Android reemplaza todos los colores de un Drawable a la vez:
 * separarlos es lo que permite teñir solo la carrocería sin perder el parabrisas/sombra.
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
     * Sprite de vehículo visto desde arriba, apuntando al norte (0°) por defecto —
     * combínalo con {@code Marker#setRotation} y {@code setFlat(true)} para que gire según
     * el rumbo real de avance, como en Uber/Didi/InDrive. Un solo color de entrada tiñe
     * sólo la carrocería; parabrisas, espejos, realce de techo y sombra quedan fijos para
     * que se lean igual en cualquier contexto (tu conductor, una unidad anónima cercana).
     */
    /**
     * Ancho base del sprite. Antes era un cuadrado de 36dp con una silueta que apenas llenaba el
     * lienzo — a ese tamaño se veía como una mancha, no como un coche (reporte del usuario sobre
     * un dispositivo real). Un sedán real no es cuadrado desde arriba: el alto sale de la misma
     * proporción 22:34 del viewport de los vectores, no de un segundo número inventado aparte.
     */
    private static final int CAR_WIDTH_DP = 34;
    private static final int CAR_HEIGHT_DP = Math.round(CAR_WIDTH_DP * 34f / 22f);

    public static BitmapDescriptor carMarker(Context context, @ColorRes int colorRes) {
        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(CAR_WIDTH_DP * density);
        int heightPx = Math.round(CAR_HEIGHT_DP * density);

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        drawLayer(context, canvas, R.drawable.ic_car_marker_shadow, widthPx, heightPx, null);
        drawLayer(context, canvas, R.drawable.ic_car_marker_body, widthPx, heightPx,
                ContextCompat.getColor(context, colorRes));
        drawLayer(context, canvas, R.drawable.ic_car_marker_detail, widthPx, heightPx, null);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private static void drawLayer(Context context, Canvas canvas, int drawableRes, int width,
                                   int height, Integer tintColor) {
        Drawable layer = ContextCompat.getDrawable(context, drawableRes);
        if (layer == null) {
            return;
        }
        if (tintColor != null) {
            layer = layer.mutate();
            layer.setTint(tintColor);
        }
        layer.setBounds(0, 0, width, height);
        layer.draw(canvas);
    }
}
