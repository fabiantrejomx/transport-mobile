package com.bng.drivo.util;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

import java.util.WeakHashMap;

/**
 * Estado de "cargando" reutilizable para cualquier botón que dispare una llamada a la API:
 * deshabilita el botón y muestra un spinner encima en vez del contenido normal (sin depender de
 * una librería nueva, solo Canvas + ValueAnimator). Aplicar en TODO botón que llama a un
 * endpoint — nunca dejar uno tocable mientras la respuesta está en vuelo.
 *
 * {@link #setLoading(TextView, boolean)} cubre MaterialButton y cualquier TextView usado como
 * botón (p. ej. una píldora con background, como btn_sos_badge) — MaterialButton usa su slot de
 * ícono nativo, cualquier otro TextView usa un compound drawable. {@link #setLoading(ImageButton,
 * boolean)} cubre botones de solo ícono (p. ej. btn_cancel_trip).
 */
public final class LoadingButtonHelper {

    private static final WeakHashMap<TextView, CharSequence> ORIGINAL_TEXT = new WeakHashMap<>();
    private static final WeakHashMap<ImageButton, Drawable> ORIGINAL_ICON = new WeakHashMap<>();

    private LoadingButtonHelper() {
    }

    public static void setLoading(TextView button, boolean loading) {
        if (loading) {
            if (!ORIGINAL_TEXT.containsKey(button)) {
                ORIGINAL_TEXT.put(button, button.getText());
            }
            button.setEnabled(false);
            button.setText("");
            SpinnerDrawable spinner = newSpinner(button.getCurrentTextColor(), button.getResources());
            if (button instanceof MaterialButton) {
                MaterialButton materialButton = (MaterialButton) button;
                materialButton.setIconPadding(0);
                materialButton.setIcon(spinner);
            } else {
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, spinner, null);
                button.setCompoundDrawablePadding(0);
            }
        } else {
            Drawable icon = button instanceof MaterialButton ? ((MaterialButton) button).getIcon()
                    : firstNonNull(button.getCompoundDrawablesRelative());
            stopIfAnimatable(icon);
            if (button instanceof MaterialButton) {
                ((MaterialButton) button).setIcon(null);
            } else {
                button.setCompoundDrawablesRelative(null, null, null, null);
            }
            button.setEnabled(true);
            CharSequence original = ORIGINAL_TEXT.remove(button);
            if (original != null) {
                button.setText(original);
            }
        }
    }

    public static void setLoading(ImageButton button, boolean loading) {
        if (loading) {
            if (!ORIGINAL_ICON.containsKey(button)) {
                ORIGINAL_ICON.put(button, button.getDrawable());
            }
            button.setEnabled(false);
            SpinnerDrawable spinner = newSpinner(currentIconColor(button), button.getResources());
            button.setImageDrawable(spinner);
        } else {
            stopIfAnimatable(button.getDrawable());
            button.setEnabled(true);
            Drawable original = ORIGINAL_ICON.remove(button);
            if (original != null) {
                button.setImageDrawable(original);
            }
        }
    }

    private static Drawable firstNonNull(Drawable[] drawables) {
        for (Drawable drawable : drawables) {
            if (drawable != null) {
                return drawable;
            }
        }
        return null;
    }

    private static void stopIfAnimatable(Drawable drawable) {
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).stop();
        }
    }

    private static int currentIconColor(ImageButton button) {
        // Los botones de solo ícono de esta app siempre tintan con colorOnSurface vía
        // app:tint en XML; no hay un getter directo, así que se resuelve por atributo de tema.
        return ColorUtils.resolveThemeColor(button.getContext(), com.google.android.material.R.attr.colorOnSurface);
    }

    private static SpinnerDrawable newSpinner(int color, android.content.res.Resources resources) {
        SpinnerDrawable spinner = new SpinnerDrawable(color);
        int sizePx = Math.round(20 * resources.getDisplayMetrics().density);
        spinner.setBounds(0, 0, sizePx, sizePx);
        spinner.start();
        return spinner;
    }

    /** Arco giratorio simple — sin dependencias nuevas, solo Canvas + ValueAnimator. */
    private static class SpinnerDrawable extends Drawable implements Animatable {

        private static final float SWEEP_ANGLE = 100f;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator animator;
        private float startAngle;

        SpinnerDrawable(int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpToPx(2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(color);

            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(800L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                startAngle = (float) animation.getAnimatedValue();
                invalidateSelf();
            });
        }

        private static float dpToPx(float dp) {
            return dp * android.content.res.Resources.getSystem().getDisplayMetrics().density;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float inset = paint.getStrokeWidth() / 2f + 1f;
            RectF bounds = new RectF(getBounds());
            bounds.inset(inset, inset);
            canvas.drawArc(bounds, startAngle, SWEEP_ANGLE, false, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void start() {
            if (!animator.isRunning()) {
                animator.start();
            }
        }

        @Override
        public void stop() {
            animator.cancel();
        }

        @Override
        public boolean isRunning() {
            return animator.isRunning();
        }
    }
}
