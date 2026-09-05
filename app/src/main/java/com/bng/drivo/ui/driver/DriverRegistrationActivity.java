package com.bng.drivo.ui.driver;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.bng.drivo.R;
import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiErrorCode;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.FirebaseStorageRepository;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.data.repository.StorageRepository;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.util.DriverFormValidators;
import com.bng.drivo.util.ImageCompressor;
import com.bng.drivo.util.LoadingButtonHelper;
import com.bng.drivo.util.PushRegistration;
import com.bng.drivo.util.SubmittedApplicationCache;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * C1 del flujo de conductor: registro en 7 pasos (ver CLAUDE.md). Las fotos se comprimen
 * localmente (ImageCompressor) y se suben directo a Cloud Storage (StorageRepository) — nunca
 * pasan por transport-api, solo la ruta resultante se registra vía POST /driver/documents.
 *
 * "No soy el propietario" (paso 5) no tiene documentos equivalentes en el enum real de
 * POST /driver/documents, así que solo se muestra un aviso de "próximamente" — decisión
 * confirmada en el plan de la Fase 7, no se bloquea el envío por esto.
 */
public class DriverRegistrationActivity extends AuthenticatedActivity {

    /**
     * Las dos fotos de la cara del conductor: la de perfil del paso 1 y la selfie de verificación
     * del paso 7. Ambas se toman con la cámara y solo con la cámara —una foto de galería no
     * verifica nada, y son justo las que la revisión manual rechaza por lentes, gorra o fleco—.
     */
    private static final String PROFILE_PHOTO = "profile_photo";
    private static final String SELFIE = "selfie";

    /**
     * Los cuatro costados del vehículo, en el orden en que se piden.
     *
     * <p>Sustituyen a la toma única de antes: con una sola foto, quien revisa no puede cotejar la
     * placa contra la tarjeta de circulación ni ver el estado real de la carrocería, y el pasajero
     * no reconoce el coche que se le acerca.
     */
    private static final List<String> VEHICLE_PHOTOS = List.of(
            "vehicle_photo_front", "vehicle_photo_right",
            "vehicle_photo_left", "vehicle_photo_rear");

    private static final Map<String, Integer> UPLOAD_LABELS = new LinkedHashMap<>();

    static {
        UPLOAD_LABELS.put(PROFILE_PHOTO, R.string.driver_reg_upload_profile_photo);
        UPLOAD_LABELS.put("license_front", R.string.driver_reg_upload_license_front);
        UPLOAD_LABELS.put("license_back", R.string.driver_reg_upload_license_back);
        UPLOAD_LABELS.put("id_front", R.string.driver_reg_upload_id_front);
        UPLOAD_LABELS.put("id_back", R.string.driver_reg_upload_id_back);
        UPLOAD_LABELS.put("vehicle_photo_front", R.string.driver_reg_upload_vehicle_photo_front);
        UPLOAD_LABELS.put("vehicle_photo_right", R.string.driver_reg_upload_vehicle_photo_right);
        UPLOAD_LABELS.put("vehicle_photo_left", R.string.driver_reg_upload_vehicle_photo_left);
        UPLOAD_LABELS.put("vehicle_photo_rear", R.string.driver_reg_upload_vehicle_photo_rear);
        UPLOAD_LABELS.put("concession", R.string.driver_reg_upload_concession);
        UPLOAD_LABELS.put("circulation_card", R.string.driver_reg_upload_circulation_card);
        UPLOAD_LABELS.put("insurance", R.string.driver_reg_upload_insurance);
        UPLOAD_LABELS.put("criminal_record", R.string.driver_reg_upload_criminal_record);
        UPLOAD_LABELS.put(SELFIE, R.string.driver_reg_upload_selfie);
    }

    private final ExecutorService compressionExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, File> pendingDocuments = new LinkedHashMap<>();
    private final Map<String, ImageView> uploadThumbs = new LinkedHashMap<>();
    private final Map<String, TextView> uploadLabels = new LinkedHashMap<>();
    private final Map<String, MaterialCardView> uploadCards = new LinkedHashMap<>();
    /** La zona tocable de cada caja de foto, para poder bloquearla mientras se envía. */
    private final Map<String, View> uploadClickables = new LinkedHashMap<>();

    /** El chevron de cada paso, por el id de su contenido: sirve para abrir el paso que trae el error. */
    private final Map<Integer, View> stepChevrons = new LinkedHashMap<>();

    private DriverRepository driverRepository;
    private StorageRepository storageRepository;
    private AuthRepository authRepository;

    private Spinner spinnerModality;
    private EditText inputCurp;
    private EditText inputRfc;
    private MaterialAutoCompleteTextView inputVehicleYear;
    private EditText inputVehicleBrand;
    private EditText inputVehicleModel;
    private EditText inputVehicleColor;
    private EditText inputVehiclePlate;
    private TextInputLayout layoutCurp;
    private TextInputLayout layoutRfc;
    private TextInputLayout layoutVehicleYear;
    private TextInputLayout layoutVehicleBrand;
    private TextInputLayout layoutVehicleModel;
    private TextInputLayout layoutVehicleColor;
    private TextInputLayout layoutVehiclePlate;
    private CheckBox checkOwner;
    private View textNotOwnerNotice;
    private View groupConcession;
    private View uploadCirculationCard;
    private View textTaxiCirculationNotice;
    private TextView textSubmitError;
    private MaterialButton btnSubmit;

    private String activePickType;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && activePickType != null) {
                    onImagePicked(activePickType, uri);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), this::onPictureTaken);

    /** El mismo contrato, pero pidiendo que abra volteada: ver {@link TakeSelfie}. */
    private final ActivityResultLauncher<Uri> selfieCameraLauncher =
            registerForActivityResult(new TakeSelfie(), this::onPictureTaken);

    /**
     * El permiso de notificaciones se pide aquí, y no más tarde.
     *
     * <p>Esta es la pantalla del conductor que acaba de mandar su expediente: lo siguiente que le
     * va a pasar es el veredicto del backoffice, que llega por push días después. Pedirlo cuando
     * ya está esperando es tarde —el aviso se pierde—, y el conductor nunca pasa por la pantalla
     * del pasajero, que era el único sitio donde se pedía.
     *
     * <p>Se concede o no, el token se registra igual: sin el permiso FCM sigue entregando el
     * mensaje y solo se pierde la alerta visual; sin token no llega absolutamente nada.
     */
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> PushRegistration.registerToken(this));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_registration);

        driverRepository = new RestDriverRepository(this);
        storageRepository = new FirebaseStorageRepository();
        authRepository = new FirebaseAuthRepository();

        bindModalitySpinner();
        bindStepToggle(R.id.header_step1, R.id.content_step1, R.id.chevron_step1);
        bindStepToggle(R.id.header_step2, R.id.content_step2, R.id.chevron_step2);
        bindStepToggle(R.id.header_step3, R.id.content_step3, R.id.chevron_step3);
        bindStepToggle(R.id.header_step4, R.id.content_step4, R.id.chevron_step4);
        bindStepToggle(R.id.header_step5, R.id.content_step5, R.id.chevron_step5);
        bindStepToggle(R.id.header_step6, R.id.content_step6, R.id.chevron_step6);
        bindStepToggle(R.id.header_step7, R.id.content_step7, R.id.chevron_step7);

        inputCurp = findViewById(R.id.input_curp);
        inputRfc = findViewById(R.id.input_rfc);
        inputVehicleYear = findViewById(R.id.input_vehicle_year);
        inputVehicleBrand = findViewById(R.id.input_vehicle_brand);
        inputVehicleModel = findViewById(R.id.input_vehicle_model);
        inputVehicleColor = findViewById(R.id.input_vehicle_color);
        inputVehiclePlate = findViewById(R.id.input_vehicle_plate);
        layoutCurp = findViewById(R.id.layout_curp);
        layoutRfc = findViewById(R.id.layout_rfc);
        layoutVehicleYear = findViewById(R.id.layout_vehicle_year);
        layoutVehicleBrand = findViewById(R.id.layout_vehicle_brand);
        layoutVehicleModel = findViewById(R.id.layout_vehicle_model);
        layoutVehicleColor = findViewById(R.id.layout_vehicle_color);
        layoutVehiclePlate = findViewById(R.id.layout_vehicle_plate);
        checkOwner = findViewById(R.id.check_owner);
        textNotOwnerNotice = findViewById(R.id.text_not_owner_notice);
        groupConcession = findViewById(R.id.group_concession);
        uploadCirculationCard = findViewById(R.id.upload_circulation_card);
        textTaxiCirculationNotice = findViewById(R.id.text_taxi_circulation_notice);
        textSubmitError = findViewById(R.id.text_submit_error);
        btnSubmit = findViewById(R.id.btn_submit_application);

        checkOwner.setOnCheckedChangeListener((buttonView, isChecked) ->
                textNotOwnerNotice.setVisibility(isChecked ? View.GONE : View.VISIBLE));

        bindYearPicker();
        clearErrorWhileTyping(layoutCurp, inputCurp);
        clearErrorWhileTyping(layoutRfc, inputRfc);
        clearErrorWhileTyping(layoutVehicleBrand, inputVehicleBrand);
        clearErrorWhileTyping(layoutVehicleModel, inputVehicleModel);
        clearErrorWhileTyping(layoutVehicleColor, inputVehicleColor);
        clearErrorWhileTyping(layoutVehiclePlate, inputVehiclePlate);

        // Antes solo se validaba al tocar "Enviar", que en un formulario de 7 pasos con scroll
        // puede quedar lejos de donde el conductor está tecleando: escribía un CURP incompleto,
        // pasaba al siguiente campo y no veía ninguna señal de que algo estaba mal hasta el
        // final. Validar también al salir del campo hace visible el error en el momento en que
        // ya se puede juzgar el valor completo — no antes, mientras todavía está a medio escribir.
        inputCurp.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validateCurp();
            }
        });
        inputRfc.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validateRfc();
            }
        });
        inputVehiclePlate.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validatePlate();
            }
        });

        bindUploadBox(R.id.upload_profile_photo, PROFILE_PHOTO);
        bindUploadBox(R.id.upload_license_front, "license_front");
        bindUploadBox(R.id.upload_license_back, "license_back");
        bindUploadBox(R.id.upload_id_front, "id_front");
        bindUploadBox(R.id.upload_id_back, "id_back");
        bindUploadBox(R.id.upload_vehicle_photo_front, "vehicle_photo_front");
        bindUploadBox(R.id.upload_vehicle_photo_right, "vehicle_photo_right");
        bindUploadBox(R.id.upload_vehicle_photo_left, "vehicle_photo_left");
        bindUploadBox(R.id.upload_vehicle_photo_rear, "vehicle_photo_rear");
        bindUploadBox(R.id.upload_concession, "concession");
        bindUploadBox(R.id.upload_circulation_card, "circulation_card");
        bindUploadBox(R.id.upload_insurance, "insurance");
        bindUploadBox(R.id.upload_criminal_record, "criminal_record");
        bindUploadBox(R.id.upload_selfie, SELFIE);

        btnSubmit.setOnClickListener(v -> attemptSubmit());

        // Aquí no hay otro diálogo de permiso compitiendo (la cámara va por FileProvider), así
        // que se puede pedir directo: Android solo admite uno pendiente a la vez.
        if (PushRegistration.needsNotificationPermission(this)) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } else {
            PushRegistration.registerToken(this);
        }
    }

    private void bindModalitySpinner() {
        spinnerModality = findViewById(R.id.spinner_modality);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.driver_reg_modality_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModality.setAdapter(adapter);
        spinnerModality.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                applyModality(position == 1);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                applyModality(false);
            }
        });
    }

    /**
     * Concesión o tarjeta de circulación, nunca las dos.
     *
     * <p>El servidor exige una <b>u</b> otra según la modalidad y rechaza la que no toca con
     * {@code DOCUMENT_NOT_REQUIRED} (ver {@code DriverApplicationService.requiredFor}). Pedir las
     * dos, como se hacía antes, dejaba al taxista sin poder terminar el registro: subía la foto a
     * Cloud Storage, el servidor la rechazaba al registrarla, y el alta moría ahí con el archivo
     * ya arriba.
     */
    private void applyModality(boolean taxi) {
        groupConcession.setVisibility(taxi ? View.VISIBLE : View.GONE);
        uploadCirculationCard.setVisibility(taxi ? View.GONE : View.VISIBLE);
        textTaxiCirculationNotice.setVisibility(taxi ? View.VISIBLE : View.GONE);
    }

    private void bindStepToggle(int headerId, int contentId, int chevronId) {
        View header = findViewById(headerId);
        View content = findViewById(contentId);
        View chevron = findViewById(chevronId);
        header.setOnClickListener(v -> {
            boolean expanding = content.getVisibility() != View.VISIBLE;
            content.setVisibility(expanding ? View.VISIBLE : View.GONE);
            chevron.setRotation(expanding ? 180f : 0f);
        });
        stepChevrons.put(contentId, chevron);
    }

    /**
     * Llena el desplegable de años: del que entra hacia atrás, diez años de antigüedad.
     *
     * <p>Se arma aquí y no en el XML porque la ventana entera se corre sola con el calendario —el
     * 1 de enero entra un modelo nuevo por arriba y sale el más viejo por abajo—, y una lista
     * escrita a mano ofrecería años que el servidor ya rechaza.
     */
    private void bindYearPicker() {
        int max = DriverFormValidators.maxVehicleYear();
        String[] years = new String[max - DriverFormValidators.minVehicleYear() + 1];
        for (int i = 0; i < years.length; i++) {
            years[i] = String.valueOf(max - i);
        }
        inputVehicleYear.setSimpleItems(years);
        inputVehicleYear.setOnItemClickListener((parent, view, position, id) ->
                layoutVehicleYear.setError(null));
    }

    /**
     * Quita la marca de error en cuanto el conductor corrige.
     *
     * <p>Sin esto el campo se queda en rojo mientras lo reescribe, que es exactamente cuando ya está
     * haciendo lo correcto.
     */
    private void clearErrorWhileTyping(TextInputLayout layout, EditText input) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                layout.setError(null);
            }
        });
    }

    private void bindUploadBox(int includeId, String type) {
        View root = findViewById(includeId);
        View clickable = root.findViewById(R.id.box_upload_content);
        ImageView thumb = root.findViewById(R.id.box_upload_thumb);
        TextView label = root.findViewById(R.id.box_upload_label);
        Integer labelRes = UPLOAD_LABELS.get(type);
        if (labelRes != null) {
            label.setText(labelRes);
        }
        clickable.setOnClickListener(v -> {
            hideKeyboardAndClearFocus();
            activePickType = type;
            if (isFacePhoto(type)) {
                showFacePhotoRequirements(type);
            } else {
                showImageSourceChooser();
            }
        });
        uploadThumbs.put(type, thumb);
        uploadLabels.put(type, label);
        uploadCards.put(type, (MaterialCardView) root);
        uploadClickables.put(type, clickable);
    }

    /** El foco no se pierde solo al tocar un botón que no es un input — si se queda en el
     * EditText anterior, Android reabre el teclado al volver de la cámara/galería. */
    private void hideKeyboardAndClearFocus() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        focused.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private void showImageSourceChooser() {
        new MaterialAlertDialogBuilder(this)
                .setItems(new CharSequence[]{
                        getString(R.string.driver_reg_source_camera), getString(R.string.driver_reg_source_gallery)
                }, (dialog, which) -> {
                    if (which == 0) {
                        launchCamera();
                    } else {
                        pickImageLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private static boolean isFacePhoto(String type) {
        return PROFILE_PHOTO.equals(type) || SELFIE.equals(type);
    }

    /**
     * Las fotos de la cara no pasan por el selector de origen: se toman en el momento.
     *
     * <p>Son los documentos que amarran al conductor con su identificación, así que permitir
     * "elegir de galería" convertiría el paso en un trámite que cualquiera pasa con una foto ajena.
     *
     * <p>El diálogo lista los requisitos <b>antes</b> de abrir la cámara y no después: son los
     * mismos de una foto para documentos oficiales, y descubrirlos al ser rechazado significa
     * repetir una revisión manual que tarda días.
     */
    private void showFacePhotoRequirements(String type) {
        boolean selfie = SELFIE.equals(type);
        new MaterialAlertDialogBuilder(this)
                .setTitle(selfie ? R.string.driver_reg_selfie_dialog_title
                        : R.string.driver_reg_profile_photo_dialog_title)
                .setMessage(R.string.driver_reg_face_photo_message)
                .setNegativeButton(R.string.driver_reg_face_photo_cancel, null)
                .setPositiveButton(selfie ? R.string.driver_reg_selfie_dialog_confirm
                                : R.string.driver_reg_profile_photo_dialog_confirm,
                        (dialog, which) -> launchCamera())
                .show();
    }

    private void onPictureTaken(boolean success) {
        if (success && pendingCameraUri != null && activePickType != null) {
            onImagePicked(activePickType, pendingCameraUri);
        }
    }

    private void launchCamera() {
        try {
            File output = File.createTempFile("capture_", ".jpg", getCacheDir());
            pendingCameraUri = FileProvider.getUriForFile(this, "com.bng.drivo.fileprovider", output);
            if (isFacePhoto(activePickType)) {
                selfieCameraLauncher.launch(pendingCameraUri);
            } else {
                cameraLauncher.launch(pendingCameraUri);
            }
        } catch (IOException | android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.driver_reg_photo_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void onImagePicked(String type, Uri sourceUri) {
        TextView label = uploadLabels.get(type);
        if (label != null) {
            label.setText(R.string.driver_reg_processing_photo);
        }
        compressionExecutor.execute(() -> {
            try {
                File output = new File(getCacheDir(), "driver_doc_" + type + ".jpg");
                ImageCompressor.compress(this, sourceUri, output);
                runOnUiThread(() -> onImageCompressed(type, output));
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.driver_reg_photo_error, Toast.LENGTH_SHORT).show();
                    Integer labelRes = UPLOAD_LABELS.get(type);
                    if (label != null && labelRes != null) {
                        label.setText(labelRes);
                    }
                });
            }
        });
    }

    private void onImageCompressed(String type, File file) {
        pendingDocuments.put(type, file);
        ImageView thumb = uploadThumbs.get(type);
        TextView label = uploadLabels.get(type);
        MaterialCardView card = uploadCards.get(type);
        if (thumb != null) {
            thumb.setImageURI(Uri.fromFile(file));
        }
        if (label != null) {
            Integer labelRes = UPLOAD_LABELS.get(type);
            String base = labelRes != null ? getString(labelRes) : type;
            label.setText(base + getString(R.string.driver_reg_upload_done_suffix));
            label.setTextColor(getColor(R.color.drivo_success));
        }
        if (card != null) {
            card.setStrokeColor(getColor(R.color.drivo_success));
        }
    }

    private void attemptSubmit() {
        // Si el teclado se queda arriba tapa justo el campo que se va a marcar en rojo; y si la
        // solicitud sí sale, quedaría abierto sobre un formulario que ya nadie puede editar.
        hideKeyboardAndClearFocus();
        textSubmitError.setVisibility(View.GONE);
        clearFieldErrors();

        // Se envía lo normalizado, no lo tecleado: el servidor guarda el CURP en mayúsculas y la
        // placa sin guiones de todos modos (ver DriverIdentifiers en transport-api), así que la
        // copia local tiene que quedar igual o "Mi Vehículo" mostraría otra placa que el pasajero.
        String curp = DriverFormValidators.normalizeCurp(inputCurp.getText().toString());
        String rfc = DriverFormValidators.normalizeRfc(inputRfc.getText().toString());
        String plate = DriverFormValidators.normalizePlate(inputVehiclePlate.getText().toString());
        String brand = inputVehicleBrand.getText().toString().trim();
        String model = inputVehicleModel.getText().toString().trim();
        String color = inputVehicleColor.getText().toString().trim();
        String yearText = inputVehicleYear.getText().toString().trim();
        boolean isOwner = checkOwner.isChecked();
        boolean isTaxi = spinnerModality.getSelectedItemPosition() == 1;
        String modality = isTaxi ? "taxi" : "particular";

        // El primero que falla es al que hay que llevar al conductor; los demás se marcan igual
        // para que vea de una vez todo lo que tiene que corregir.
        TextInputLayout firstBad = null;

        if (!validateCurp()) {
            firstBad = layoutCurp;
        }
        if (!validateRfc()) {
            firstBad = firstBad != null ? firstBad : layoutRfc;
        }

        // El año sale del desplegable, así que ya viene dentro de la ventana; se comprueba de
        // todos modos porque un valor restaurado (autocompletado, estado de instancia de una
        // versión anterior) llegaría hasta aquí y lo rechazaría el servidor con 422 después de
        // haber subido las once fotos.
        int year = parseYear(yearText);
        if (yearText.isEmpty() || !DriverFormValidators.isValidVehicleYear(year)) {
            firstBad = markError(firstBad, layoutVehicleYear, R.string.driver_reg_invalid_year_error);
        }
        if (brand.isEmpty()) {
            firstBad = markError(firstBad, layoutVehicleBrand, R.string.driver_reg_required_field_error);
        }
        if (model.isEmpty()) {
            firstBad = markError(firstBad, layoutVehicleModel, R.string.driver_reg_required_field_error);
        }
        if (color.isEmpty()) {
            firstBad = markError(firstBad, layoutVehicleColor, R.string.driver_reg_required_field_error);
        }

        if (!validatePlate()) {
            firstBad = firstBad != null ? firstBad : layoutVehiclePlate;
        }

        if (firstBad != null) {
            showSubmitError(R.string.driver_reg_fix_fields_error);
            revealField(firstBad);
            return;
        }

        Deque<String> requiredTypes = new ArrayDeque<>();
        requiredTypes.add(PROFILE_PHOTO);
        requiredTypes.add("license_front");
        requiredTypes.add("license_back");
        requiredTypes.add("id_front");
        requiredTypes.add("id_back");
        requiredTypes.addAll(VEHICLE_PHOTOS);
        // Una u otra, igual que requiredFor() en el servidor. Mandar la que no toca da 422.
        requiredTypes.add(isTaxi ? "concession" : "circulation_card");
        requiredTypes.add("insurance");
        requiredTypes.add("criminal_record");
        requiredTypes.add(SELFIE);

        for (String type : requiredTypes) {
            if (!pendingDocuments.containsKey(type)) {
                showSubmitError(R.string.driver_reg_missing_photos_error);
                MaterialCardView missing = uploadCards.get(type);
                if (missing != null) {
                    revealField(missing);
                }
                return;
            }
        }

        setSubmitting(true);
        driverRepository.submitApplication(modality, curp, rfc, brand, model, color, plate,
                year, isOwner, new ApiCallback<DriverApplication>() {
                    @Override
                    public void onSuccess(DriverApplication result) {
                        // El contrato no devuelve estos datos en ningún GET, así que se guardan
                        // aquí: es la única copia que queda de lo que se envió a revisión. Hoy
                        // nadie la lee —Configuración ya no muestra vehículo ni documentos—, pero
                        // se sigue escribiendo porque perderla dejaría esa pantalla sin nada que
                        // enseñar el día que se retome (ver SubmittedApplicationCache).
                        SubmittedApplicationCache.save(DriverRegistrationActivity.this, modality, curp,
                                rfc, brand, model, color, plate, year, isOwner);
                        uploadDocuments(requiredTypes);
                    }

                    @Override
                    public void onError(ApiException error) {
                        setSubmitting(false);
                        showApplicationError(error);
                    }
                });
    }

    /**
     * El servidor tiene la última palabra sobre estos cuatro campos.
     *
     * <p>Repite las mismas reglas que {@link DriverFormValidators}, así que en teoría no debería
     * rechazar nada que haya pasado por aquí. Se traduce campo por campo de todos modos: el día que
     * las dos copias se separen —un formato de placa nuevo, un ajuste del SAT— el conductor tiene
     * que ver <b>cuál</b> dato le rechazaron, no un "intenta de nuevo" sobre un formulario de 7
     * pasos que acaba de llenar entero.
     */
    private void showApplicationError(ApiException error) {
        ApiErrorCode code = error.getCode();
        TextInputLayout field = null;
        if (code == ApiErrorCode.INVALID_CURP) {
            field = markError(null, layoutCurp, R.string.driver_reg_curp_invalid_error);
        } else if (code == ApiErrorCode.INVALID_RFC) {
            field = markError(null, layoutRfc, R.string.driver_reg_rfc_invalid_error);
        } else if (code == ApiErrorCode.INVALID_PLATE) {
            field = markError(null, layoutVehiclePlate, R.string.driver_reg_plate_invalid_error);
        } else if (code == ApiErrorCode.INVALID_VEHICLE_YEAR) {
            field = markError(null, layoutVehicleYear, R.string.driver_reg_invalid_year_error);
        }

        if (field == null) {
            showSubmitError(R.string.driver_reg_submit_application_error);
            return;
        }
        showSubmitError(R.string.driver_reg_fix_fields_error);
        revealField(field);
    }

    /** @return el año elegido, o 0 si el campo no trae uno: eso lo rechaza {@code isValidVehicleYear}. */
    private static int parseYear(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** @return el primer campo marcado, para saber a cuál llevar al conductor. */
    private TextInputLayout markError(TextInputLayout firstBad, TextInputLayout layout, int messageRes) {
        layout.setError(getString(messageRes));
        return firstBad != null ? firstBad : layout;
    }

    /**
     * Valida CURP, RFC y placa; se usa tanto al perder el foco del campo como al tocar "Enviar".
     *
     * <p>Antes solo se llamaba desde {@code attemptSubmit()}, y en un formulario de 7 pasos con
     * scroll eso deja al conductor sin ninguna señal mientras sigue tecleando: escribe un CURP a
     * medias, pasa al campo siguiente, y no hay nada en pantalla que le diga que algo está mal
     * hasta que llega al final y toca enviar. Validar también al salir del campo hace visible el
     * error en el momento justo en que ya se puede juzgar el valor completo.
     *
     * <p>Cada método deja el error puesto en su {@link TextInputLayout} y no lo limpia si todo
     * está bien —eso ya lo hace {@link #clearErrorWhileTyping} en cuanto el conductor vuelve a
     * escribir—, salvo que se llame fuera de ese flujo (el blur), donde sí hace falta limpiar el
     * error de un intento anterior una vez que el valor ya quedó correcto.
     */
    private boolean validateCurp() {
        String curp = DriverFormValidators.normalizeCurp(inputCurp.getText().toString());
        if (curp.isEmpty()) {
            layoutCurp.setError(getString(R.string.driver_reg_required_field_error));
            return false;
        }
        if (!DriverFormValidators.isValidCurp(curp)) {
            layoutCurp.setError(getString(R.string.driver_reg_curp_invalid_error));
            return false;
        }
        layoutCurp.setError(null);
        return true;
    }

    private boolean validateRfc() {
        String rfc = DriverFormValidators.normalizeRfc(inputRfc.getText().toString());
        if (rfc.isEmpty()) {
            layoutRfc.setError(getString(R.string.driver_reg_required_field_error));
            return false;
        }
        if (!DriverFormValidators.isValidRfc(rfc)) {
            layoutRfc.setError(getString(R.string.driver_reg_rfc_invalid_error));
            return false;
        }
        layoutRfc.setError(null);
        return true;
    }

    private boolean validatePlate() {
        String plate = DriverFormValidators.normalizePlate(inputVehiclePlate.getText().toString());
        if (plate.isEmpty()) {
            layoutVehiclePlate.setError(getString(R.string.driver_reg_required_field_error));
            return false;
        }
        if (!DriverFormValidators.isValidPlate(plate)) {
            layoutVehiclePlate.setError(getString(R.string.driver_reg_plate_invalid_error));
            return false;
        }
        layoutVehiclePlate.setError(null);
        return true;
    }

    private void clearFieldErrors() {
        layoutCurp.setError(null);
        layoutRfc.setError(null);
        layoutVehicleYear.setError(null);
        layoutVehicleBrand.setError(null);
        layoutVehicleModel.setError(null);
        layoutVehicleColor.setError(null);
        layoutVehiclePlate.setError(null);
    }

    /**
     * Abre el paso que contiene el campo —o la foto— y lo deja a la vista.
     *
     * <p>Los 7 pasos se pliegan, así que marcar el campo en rojo no basta: si el paso está cerrado
     * el conductor solo ve "revisa los campos marcados" sin ningún campo marcado a la vista. El
     * paso se busca subiendo por los padres hasta dar con uno registrado en {@code stepChevrons},
     * para no tener que mantener a mano un mapa de campo a paso.
     */
    private void revealField(View field) {
        View view = field;
        while (view != null) {
            View chevron = stepChevrons.get(view.getId());
            if (chevron != null) {
                if (view.getVisibility() != View.VISIBLE) {
                    view.setVisibility(View.VISIBLE);
                    chevron.setRotation(180f);
                }
                break;
            }
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        // Después de expandir: el ScrollView solo puede llevarlo a algo que ya tiene medida.
        // Un campo de texto se enfoca —eso lo desplaza y abre el teclado para corregirlo ya—;
        // una caja de foto no es enfocable, así que solo se desplaza hasta ella.
        field.post(() -> {
            if (field instanceof TextInputLayout) {
                field.requestFocus();
            } else {
                field.requestRectangleOnScreen(
                        new Rect(0, 0, field.getWidth(), field.getHeight()), false);
            }
        });
    }

    private void uploadDocuments(Deque<String> remainingTypes) {
        String type = remainingTypes.poll();
        if (type == null) {
            setSubmitting(false);
            Toast.makeText(this, R.string.driver_reg_success_toast, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, DriverHomeActivity.class));
            finish();
            return;
        }

        File file = pendingDocuments.get(type);
        String uid = authRepository.getCurrentUserId();
        storageRepository.uploadDriverDocument(uid, type, file, new StorageRepository.UploadCallback() {
            @Override
            public void onSuccess(String storagePath) {
                driverRepository.registerDocument(type, storagePath, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        uploadDocuments(remainingTypes);
                    }

                    @Override
                    public void onError(ApiException error) {
                        onDocumentUploadFailed(type);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                onDocumentUploadFailed(type);
            }
        });
    }

    private void onDocumentUploadFailed(String type) {
        setSubmitting(false);
        Integer labelRes = UPLOAD_LABELS.get(type);
        String label = labelRes != null ? getString(labelRes) : type;
        textSubmitError.setText(getString(R.string.driver_reg_upload_document_error, label));
        textSubmitError.setVisibility(View.VISIBLE);
    }

    /**
     * Estado de envío: spinner en el botón y el formulario entero bloqueado.
     *
     * <p>Enviar no es una sola llamada, son doce —el expediente y luego cada foto, una tras otra—,
     * y durante todo ese rato la pantalla sigue a la vista. Sin bloquear, cambiar la placa o
     * reemplazar una foto a media subida dejaría el expediente revuelto: los datos de una captura y
     * las fotos de otra, sin que nada avise. Lo que se manda es lo que estaba al tocar "Enviar".
     *
     * <p>Al fallar se libera todo antes de pintar los errores, para que el conductor pueda corregir
     * justo el campo que le marcaron.
     */
    private void setSubmitting(boolean submitting) {
        LoadingButtonHelper.setLoading(btnSubmit, submitting);
        setFormEnabled(!submitting);
    }

    private void setFormEnabled(boolean enabled) {
        spinnerModality.setEnabled(enabled);
        layoutCurp.setEnabled(enabled);
        layoutRfc.setEnabled(enabled);
        layoutVehicleYear.setEnabled(enabled);
        layoutVehicleBrand.setEnabled(enabled);
        layoutVehicleModel.setEnabled(enabled);
        layoutVehicleColor.setEnabled(enabled);
        layoutVehiclePlate.setEnabled(enabled);
        checkOwner.setEnabled(enabled);

        // Las cajas de foto no tienen estado deshabilitado propio: se apagan y se atenúan a mano
        // para que se note que están bloqueadas, igual que los campos de texto.
        for (Map.Entry<String, View> entry : uploadClickables.entrySet()) {
            entry.getValue().setEnabled(enabled);
            MaterialCardView card = uploadCards.get(entry.getKey());
            if (card != null) {
                card.setAlpha(enabled ? 1f : 0.5f);
            }
        }
    }

    private void showSubmitError(int stringRes) {
        textSubmitError.setText(stringRes);
        textSubmitError.setVisibility(View.VISIBLE);
    }

    /**
     * {@code TakePicture} pidiendo que la cámara abra con el lente frontal.
     *
     * <p>Son <b>pistas, no una garantía</b>: {@code ACTION_IMAGE_CAPTURE} no tiene forma oficial de
     * elegir lente, así que cada app de cámara lee la suya —de ahí que se manden las tres que
     * reconocen AOSP, Google Camera y la mayoría de las capas de fabricante—. Si el teléfono ignora
     * las tres, abre con la trasera y el conductor la voltea a mano: la foto sale igual.
     *
     * <p>El valor 1 es el de {@code Camera.CameraInfo.CAMERA_FACING_FRONT}, el que espera este
     * extra por herencia. Ojo: no es el de {@code CameraCharacteristics.LENS_FACING_FRONT}, que
     * vale 0 — usarlo pediría justamente la trasera.
     */
    private static class TakeSelfie extends ActivityResultContracts.TakePicture {

        private static final int CAMERA_FACING_FRONT = 1;

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull Uri input) {
            Intent intent = super.createIntent(context, input);
            intent.putExtra("android.intent.extras.CAMERA_FACING", CAMERA_FACING_FRONT);
            intent.putExtra("android.intent.extras.LENS_FACING_FRONT", CAMERA_FACING_FRONT);
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
            return intent;
        }
    }
}
