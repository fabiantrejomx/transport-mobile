package com.bng.drivo.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Alerta local de "llegó una solicitud" para el conductor: tono del sistema + vibración.
 *
 * <p>No la cubre el canal de notificaciones: el push {@code new_ride} solo suena cuando el
 * sistema pinta la notificación, y con la app abierta en el radar — que es justo donde el
 * conductor la va a estar esperando — la solicitud aparecía en silencio dentro del modal. Un
 * conductor manejando no está mirando la pantalla; sin sonido ni vibración se le iban los 20-60 s
 * que da el contrato para responder.
 *
 * <p>Usa el tono de notificación del sistema, así que respeta solo/silencio y el volumen de
 * notificaciones del teléfono sin tener que consultarlos: si el conductor tiene el teléfono en
 * silencio, no suena, y la vibración sigue avisando.
 */
public final class RideAlert {

    /** Dos pulsos separados: se distingue de una notificación cualquiera de un solo golpe. */
    private static final long[] VIBRATION_PATTERN = {0, 350, 180, 350};

    private RideAlert() {
    }

    public static void play(Context context) {
        playTone(context);
        vibrate(context);
    }

    private static void playTone(Context context) {
        Uri toneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (toneUri == null) {
            return;
        }
        Ringtone ringtone = RingtoneManager.getRingtone(context.getApplicationContext(), toneUri);
        if (ringtone == null) {
            return;
        }
        ringtone.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        ringtone.play();
    }

    private static void vibrate(Context context) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            vibrator = manager != null ? manager.getDefaultVibrator() : null;
        } else {
            vibrator = context.getSystemService(Vibrator.class);
        }
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1));
    }
}
