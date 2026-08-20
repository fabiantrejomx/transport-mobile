package com.bng.drivo.data.repository;

import android.app.Activity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

/**
 * Autenticación por teléfono con Firebase Auth (sin contraseña). El backend
 * ({@code transport-api}) usa el ID token de Firebase como Bearer, así que no
 * hay ningún registro/login propio: el mismo verifyCode() sirve tanto para un
 * número nuevo como uno existente.
 */
public class FirebaseAuthRepository implements AuthRepository {

    private static final long CODE_TIMEOUT_SECONDS = 60L;

    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

    private String verificationId;

    @Override
    public boolean isLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    @Override
    public String getCurrentUserId() {
        return firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getUid() : null;
    }

    @Override
    public void sendVerificationCode(Activity activity, String phoneNumber, OtpSendCallback callback) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(CODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        firebaseAuth.signInWithCredential(credential)
                                .addOnSuccessListener(result -> callback.onAutoVerified())
                                .addOnFailureListener(e -> callback.onError(e.getMessage()));
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        callback.onError(e.getMessage());
                    }

                    @Override
                    public void onCodeSent(String id, PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = id;
                        callback.onCodeSent(id);
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    @Override
    public void verifyCode(String smsCode, OtpVerifyCallback callback) {
        if (verificationId == null) {
            callback.onError("No hay una verificación en curso. Solicita el código de nuevo.");
            return;
        }
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, smsCode);
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    @Override
    public void logout() {
        firebaseAuth.signOut();
    }
}
