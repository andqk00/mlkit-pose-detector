package com.example.mlkitposedetector.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.mlkitposedetector.CameraSource;
import com.example.mlkitposedetector.CameraSourcePreview;
import com.example.mlkitposedetector.GraphicOverlay;
import com.example.mlkitposedetector.R;
import com.example.mlkitposedetector.activity.posedetector.PoseDetectorProcessor;
import com.example.mlkitposedetector.preference.PreferenceUtils;
import com.example.mlkitposedetector.preference.SettingsActivity;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LivePreviewActivity extends AppCompatActivity
    implements ActivityCompat.OnRequestPermissionsResultCallback, CompoundButton.OnCheckedChangeListener,
        TextToSpeech.OnInitListener {

    private static final String POSE_DETECTION = "Pose Detection";

    private static final String TAG = "LivePreviewActivity";
    private static final int PERMISSION_REQUESTS = 1;

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private String selectedModel = POSE_DETECTION;

    private TextToSpeech textToSpeech = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_vision_live_preview);

        textToSpeech = new TextToSpeech(this, this);

        preview = findViewById(R.id.preview_view);
        if(preview == null) Log.d(TAG, "Preview is null");
        graphicOverlay = findViewById(R.id.graphic_overlay);
        if(graphicOverlay == null) Log.d(TAG, "GraphicOverlay is null");

        TextView detect = findViewById(R.id.detect);
        detect.setText(POSE_DETECTION);

        ToggleButton facingSwitch = findViewById(R.id.facing_switch);
        facingSwitch.setOnCheckedChangeListener(this);

        ImageView settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(
            v -> {
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                intent.putExtra(
                    SettingsActivity.EXTRA_LAUNCH_SOURCE, SettingsActivity.LaunchSource.LIVE_PREVIEW
                );
                startActivity(intent);
            }
        );

        if (allPermissionsGranted()) createCameraSource(selectedModel);
        else getRuntimePermissions();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "Set facing");
        if (cameraSource != null) {
            if (isChecked) {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
            } else {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
            }
        }
        preview.stop();
        startCameraSource();
    }

    private void createCameraSource(String model) {
        // If there's no existing camera source, create one
        if (cameraSource == null) cameraSource = new CameraSource(this, graphicOverlay);

        try {
            if (model != POSE_DETECTION) Log.e(TAG, "Unknown model: " + model);
            PoseDetectorOptionsBase poseDetectorOptions =
                PreferenceUtils.getPoseDetectorOptionsForLivePreview(this);
            Log.i(TAG, "Using Pose Detector with options: " + poseDetectorOptions);
            boolean shouldShowInFrameLikelihood =
                PreferenceUtils.shouldShowPoseDetectionInFrameLikelihoodLivePreview(this);
            boolean visualizeZ = PreferenceUtils.shouldPoseDetectionVisualizeZ(this);
            boolean rescaleZ = PreferenceUtils.shouldPoseDetectionRescaleZForVisualization(this);
            boolean runClassification = PreferenceUtils.shouldPoseDetectionRunClassification(this);
            cameraSource.setMachineLearningFrameProcessor(
                new PoseDetectorProcessor(
                    this,
                    poseDetectorOptions,
                    shouldShowInFrameLikelihood,
                    visualizeZ,
                    rescaleZ,
                    runClassification,
                    /* isStreamMode */ true,
                    textToSpeech
                )
            );
        } catch (RuntimeException e) {
            Log.e(TAG, "Can not create image processor: " + model, e);
            Toast.makeText(
                getApplicationContext(),
                "Can not create image processor: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "Resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "Resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        createCameraSource(selectedModel);
        startCameraSource();
    }

    /** Stops the camera. */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
        textToSpeech.shutdown();
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        Log.i(TAG, "Permission granted!");
        if (allPermissionsGranted()) {
            createCameraSource(selectedModel);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }

    @Override
    public void onInit(int status) {
        Log.i(TAG, "onInit [" + status + "]");
    }
}