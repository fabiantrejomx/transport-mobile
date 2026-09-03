package com.bng.drivo.data.repository;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.bng.drivo.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

/**
 * Autenticación con Firebase Auth (sin contraseña), por teléfono o con Google. El backend
 * ({@code transport-api}) usa el ID token de Firebase como Bearer, así que no hay ningún
 * registro/login propio: tanto verifyCode() como signInWithGoogle() sirven igual para una
 * cuenta nueva que para una existente.
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

    /**
     * Dos saltos: Google devuelve su propio id token a través de la hoja de cuentas del sistema, y
     * ese token se canjea por una sesión de Firebase. El backend solo ve el segundo, así que a
     * partir de {@code onSuccess()} no hay nada que distinga esta vía del OTP.
     */
    @Override
    public void signInWithGoogle(Activity activity, GoogleSignInCallback callback) {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                // El id de cliente web (oauth_client type 3) lo genera el plugin de
                // google-services al leer google-services.json: no se escribe a mano, y así no
                // puede quedar desfasado si el archivo se regenera.
                .setServerClientId(activity.getString(R.string.default_web_client_id))
                // Sin esto la hoja solo ofrece cuentas que ya usaron la app antes, y la primera
                // vez —que es justo el alta— saldría vacía.
                .setFilterByAuthorizedAccounts(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        CredentialManager.create(activity).getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                // En el hilo principal: quien recibe esto pinta Toasts y cambia de pantalla.
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        signInToFirebase(response, callback);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            callback.onCancelled();
                        } else if (e instanceof NoCredentialException) {
                            // No hay ninguna cuenta de Google en el teléfono. Es lo único que el
                            // usuario puede resolver por su cuenta, así que se distingue del
                            // resto en vez de caer en un "algo salió mal".
                            callback.onError(activity.getString(R.string.auth_google_no_accounts));
                        } else {
                            callback.onError(e.getMessage());
                        }
                    }
                });
    }

    private void signInToFirebase(GetCredentialResponse response, GoogleSignInCallback callback) {
        if (!(response.getCredential() instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        .equals(response.getCredential().getType())) {
            // La API es genérica (también sirve contraseñas y passkeys) y solo pedimos una cosa,
            // así que esto no debería pasar; si pasa, es un error nuestro y no del usuario.
            callback.onError("Credencial inesperada: " + response.getCredential().getType());
            return;
        }

        CustomCredential credential = (CustomCredential) response.getCredential();
        String idToken = GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();

        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    @Override
    public void logout() {
        firebaseAuth.signOut();
    }
}
