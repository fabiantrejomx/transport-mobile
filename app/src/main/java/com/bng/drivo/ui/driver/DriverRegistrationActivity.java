package com.bng.drivo.ui.driver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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
import androidx.core.content.FileProvider;

import com.bng.drivo.R;
import com.bng.drivo.data.model.DriverApplication;
import com.bng.drivo.data.remote.ApiCallback;
import com.bng.drivo.data.remote.ApiException;
import com.bng.drivo.data.repository.AuthRepository;
import com.bng.drivo.data.repository.DriverRepository;
import com.bng.drivo.data.repository.FirebaseAuthRepository;
import com.bng.drivo.data.repository.FirebaseStorageRepository;
import com.bng.drivo.data.repository.RestDriverRepository;
import com.bng.drivo.data.repository.StorageRepository;
import com.bng.drivo.ui.auth.AuthenticatedActivity;
import com.bng.drivo.util.ImageCompressor;
import com.bng.drivo.util.LoadingButtonHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.time.Year;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
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

    private static final Map<String, Integer> UPLOAD_LABELS = new LinkedHashMap<>();

    static {
        UPLOAD_LABELS.put("profile_photo", R.string.driver_reg_upload_profile_photo);
        UPLOAD_LABELS.put("license_front", R.string.driver_reg_upload_license_front);
        UPLOAD_LABELS.put("license_back", R.string.driver_reg_upload_license_back);
        UPLOAD_LABELS.put("id_front", R.string.driver_reg_upload_id_front);
        UPLOAD_LABELS.put("id_back", R.string.driver_reg_upload_id_back);
        UPLOAD_LABELS.put("vehicle_photo", R.string.driver_reg_upload_vehicle_photo);
        UPLOAD_LABELS.put("concession", R.string.driver_reg_upload_concession);
        UPLOAD_LABELS.put("circulation_card", R.string.driver_reg_upload_circulation_card);
        UPLOAD_LABELS.put("insurance", R.string.driver_reg_upload_insurance);
        UPLOAD_LABELS.put("criminal_record", R.string.driver_reg_upload_criminal_record);
        UPLOAD_LABELS.put("selfie", R.string.driver_reg_upload_selfie);
    }

    private final ExecutorService compressionExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, File> pendingDocuments = new LinkedHashMap<>();
    private final Map<String, ImageView> uploadThumbs = new LinkedHashMap<>();
    private final Map<String, TextView> uploadLabels = new LinkedHashMap<>();
    private final Map<String, MaterialCardView> uploadCards = new LinkedHashMap<>();

    private DriverRepository driverRepository;
    private StorageRepository storageRepository;
    private AuthRepository authRepository;

    private Spinner spinnerModality;
    private EditText inputCurp;
    private EditText inputRfc;
    private EditText inputVehicleYear;
    private EditText inputVehicleBrand;
    private EditText inputVehicleModel;
    private EditText inputVehicleColor;
    private EditText inputVehiclePlate;
    private CheckBox checkOwner;
    private View textNotOwnerNotice;
    private View groupConcession;
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
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraUri != null && activePickType != null) {
                    onImagePicked(activePickType, pendingCameraUri);
                }
            });

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
        checkOwner = findViewById(R.id.check_owner);
        textNotOwnerNotice = findViewById(R.id.text_not_owner_notice);
        groupConcession = findViewById(R.id.group_concession);
        textSubmitError = findViewById(R.id.text_submit_error);
        btnSubmit = findViewById(R.id.btn_submit_application);

        checkOwner.setOnCheckedChangeListener((buttonView, isChecked) ->
                textNotOwnerNotice.setVisibility(isChecked ? View.GONE : View.VISIBLE));

        bindUploadBox(R.id.upload_profile_photo, "profile_photo");
        bindUploadBox(R.id.upload_license_front, "license_front");
        bindUploadBox(R.id.upload_license_back, "license_back");
        bindUploadBox(R.id.upload_id_front, "id_front");
        bindUploadBox(R.id.upload_id_back, "id_back");
        bindUploadBox(R.id.upload_vehicle_photo, "vehicle_photo");
        bindUploadBox(R.id.upload_concession, "concession");
        bindUploadBox(R.id.upload_circulation_card, "circulation_card");
        bindUploadBox(R.id.upload_insurance, "insurance");
        bindUploadBox(R.id.upload_criminal_record, "criminal_record");
        bindUploadBox(R.id.upload_selfie, "selfie");

        btnSubmit.setOnClickListener(v -> attemptSubmit());
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
                groupConcession.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                groupConcession.setVisibility(View.GONE);
            }
        });
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
            showImageSourceChooser();
        });
        uploadThumbs.put(type, thumb);
        uploadLabels.put(type, label);
        uploadCards.put(type, (MaterialCardView) root);
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

    private void launchCamera() {
        try {
            File output = File.createTempFile("capture_", ".jpg", getCacheDir());
            pendingCameraUri = FileProvider.getUriForFile(this, "com.bng.drivo.fileprovider", output);
            cameraLauncher.launch(pendingCameraUri);
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
        textSubmitError.setVisibility(View.GONE);

        String curp = inputCurp.getText().toString().trim();
        String rfc = inputRfc.getText().toString().trim();
        String yearText = inputVehicleYear.getText().toString().trim();
        String brand = inputVehicleBrand.getText().toString().trim();
        String model = inputVehicleModel.getText().toString().trim();
        String color = inputVehicleColor.getText().toString().trim();
        String plate = inputVehiclePlate.getText().toString().trim();
        boolean isOwner = checkOwner.isChecked();
        boolean isTaxi = spinnerModality.getSelectedItemPosition() == 1;
        String modality = isTaxi ? "taxi" : "particular";

        if (curp.isEmpty() || brand.isEmpty() || model.isEmpty() || color.isEmpty() || plate.isEmpty()
                || yearText.isEmpty()) {
            showSubmitError(R.string.driver_reg_missing_fields_error);
            return;
        }

        int year;
        try {
            year = Integer.parseInt(yearText);
        } catch (NumberFormatException e) {
            showSubmitError(R.string.driver_reg_invalid_year_error);
            return;
        }
        int currentYear = Year.now().getValue();
        if (year < 1990 || year > currentYear + 1) {
            showSubmitError(R.string.driver_reg_invalid_year_error);
            return;
        }

        Deque<String> requiredTypes = new ArrayDeque<>();
        requiredTypes.add("profile_photo");
        requiredTypes.add("license_front");
        requiredTypes.add("license_back");
        requiredTypes.add("id_front");
        requiredTypes.add("id_back");
        requiredTypes.add("vehicle_photo");
        if (isTaxi) {
            requiredTypes.add("concession");
        }
        requiredTypes.add("circulation_card");
        requiredTypes.add("insurance");
        requiredTypes.add("criminal_record");
        requiredTypes.add("selfie");

        for (String type : requiredTypes) {
            if (!pendingDocuments.containsKey(type)) {
                showSubmitError(R.string.driver_reg_missing_fields_error);
                return;
            }
        }

        setSubmitting(true);
        driverRepository.submitApplication(modality, curp, rfc.isEmpty() ? null : rfc, brand, model, color, plate,
                year, isOwner, new ApiCallback<DriverApplication>() {
                    @Override
                    public void onSuccess(DriverApplication result) {
                        uploadDocuments(requiredTypes);
                    }

                    @Override
                    public void onError(ApiException error) {
                        setSubmitting(false);
                        showSubmitError(R.string.driver_reg_submit_application_error);
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

    private void setSubmitting(boolean submitting) {
        LoadingButtonHelper.setLoading(btnSubmit, submitting);
    }

    private void showSubmitError(int stringRes) {
        textSubmitError.setText(stringRes);
        textSubmitError.setVisibility(View.VISIBLE);
    }
}
