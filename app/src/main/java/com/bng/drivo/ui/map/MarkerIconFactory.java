package com.bng.drivo.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
     * Ancho base del sprite. El alto sale de la misma proporción 22:34 del viewport de los
     * vectores, no de un segundo número inventado aparte: un sedán no es cuadrado visto desde
     * arriba, y era justo eso lo que hacía que un lienzo cuadrado de 36dp con la silueta perdida
     * dentro se leyera como una mancha en vez de como un coche.
     *
     * <p>Calibrado contra el mobiliario del propio mapa, que es lo que da la escala: a 34dp el
     * coche medía más del doble de alto que un pin de sitio de Google y se salía de la retícula de
     * calles; a 24dp mide alrededor de vez y media el pin, que es la proporción con la que se lee
     * como un vehículo puesto sobre la calzada. Los parabrisas y las llantas siguen resolviéndose
     * a este tamaño — a 450dpi son 67x104 píxeles reales, no hay pérdida de detalle.
     *
     * <p>Un único número para los tres usos (el propio coche del conductor, el conductor asignado
     * que ve el pasajero y las unidades cercanas del mapa de inicio): son el mismo objeto en el
     * mismo mapa y tienen que medir lo mismo.
     */
    private static final int CAR_WIDTH_DP = 24;
    private static final int CAR_HEIGHT_DP = Math.round(CAR_WIDTH_DP * 34f / 22f);

    /**
     * La flecha se dibuja más grande que el coche a propósito: en la vista de navegación la cámara
     * la ve en escorzo y pierde buena parte de su alto aparente, así que al tamaño del coche
     * quedaría diminuta justo donde más se mira.
     */
    private static final int PUCK_WIDTH_DP = 30;
    private static final int PUCK_HEIGHT_DP = 34;

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

    /**
     * Flecha de navegación, para cuando el mapa va inclinado.
     *
     * <p>El sprite del coche está dibujado <b>visto desde arriba</b> y va pegado al plano del mapa
     * ({@code setFlat(true)}), así que en cuanto la cámara se inclina 45° se ve en escorzo: el
     * rectángulo se acorta a lo largo y el sedán queda como una mancha aplastada. No es un defecto
     * del dibujo, es lo que le pasa a cualquier figura tumbada vista de canto.
     *
     * <p>La solución no es enderezar el coche —eso lo dejaría de pie sobre la calle, como una
     * calcomanía— sino usar una figura pensada para leerse en escorzo. Esta punta de flecha es la
     * misma que usan los navegadores por el mismo motivo: sigue diciendo hacia dónde apunta aunque
     * pierda la mitad de su alto.
     *
     * <p>El vértice trasero hundido es lo que la distingue de un triángulo: da la lectura de
     * "flecha" incluso cuando el escorzo se come el largo.
     */
    public static BitmapDescriptor navigationPuck(Context context, @ColorRes int colorRes) {
        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(PUCK_WIDTH_DP * density);
        int heightPx = Math.round(PUCK_HEIGHT_DP * density);
        float strokePx = 2f * density;

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Se dibuja hacia dentro del lienzo para que el borde y la sombra no se recorten.
        float left = strokePx;
        float right = widthPx - strokePx;
        float top = strokePx;
        float bottom = heightPx - strokePx;
        float midX = widthPx / 2f;

        Path arrow = new Path();
        arrow.moveTo(midX, top);                       // punta, al norte
        arrow.lineTo(right, bottom);                   // alero derecho
        arrow.lineTo(midX, bottom - (bottom - top) * 0.28f);   // vértice trasero hundido
        arrow.lineTo(left, bottom);                    // alero izquierdo
        arrow.close();

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(ContextCompat.getColor(context, colorRes));
        fill.setStyle(Paint.Style.FILL);
        // Sombra suave: sin ella la flecha se confunde con el trazo de la ruta, que va debajo y en
        // un color parecido.
        fill.setShadowLayer(3f * density, 0f, 1.5f * density, 0x66000000);
        canvas.drawPath(arrow, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokePx);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(arrow, stroke);

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
