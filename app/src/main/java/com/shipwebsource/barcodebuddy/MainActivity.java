package com.shipwebsource.barcodebuddy;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

public class MainActivity extends AppCompatActivity
{

    private static final String TAG = "BarcodeBuddyMain";
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private static final int RC_HANDLE_GMS = 9001;

    private CameraSourcePreview preview;
    private GraphicOverlay<BarcodeGraphic> overlay;
    private CameraSource cameraSource;


    private TextView scanResult;
    private ImageView flashState;

    private boolean useFlash;
    private boolean useAutoFocus;

    // helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;



    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scanResult = (TextView) findViewById(R.id.scanResult);
        preview = (CameraSourcePreview) findViewById(R.id.camera_view);
        overlay = (GraphicOverlay<BarcodeGraphic>) findViewById(R.id.graphicOverlay);
        flashState = (ImageView) findViewById(R.id.cameraFlashState);

        if(getIntent().getExtras() != null)
        {
            Bundle extras = getIntent().getExtras();
            useFlash = extras.getBoolean("Flash");
            useAutoFocus = extras.getBoolean("AutoFocus");
            if (!useFlash)
            {
                useFlash = true; // Enable the flash light
                flashState.setImageResource(R.drawable.ic_flash_on);
                Snackbar.make(overlay, "Flash is now ON", Snackbar.LENGTH_LONG).show();
            }

            else
            {
                useFlash = false; // Disable the flash light
                flashState.setImageResource(R.drawable.ic_flash_off);
                Snackbar.make(overlay, "Flash is now OFF", Snackbar.LENGTH_LONG).show();
            }
        }

        else
        {
            // Set default values for auto focus and flash. Autofocus will always be on by default
            useFlash = false;
            useAutoFocus = true;
            Snackbar.make(overlay, "Tap to capture. Pinch/Stretch to zoom", Snackbar.LENGTH_LONG).show();
        }


        flashState.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {

                toggleFlash();

            }
        });

        // Check for the camera permission before accessing the camera.  If the permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED)
        {
            createCameraSource(useAutoFocus, useFlash);
        }
        else
        {
            requestCameraPermission();
        }

        gestureDetector = new GestureDetector(this, new CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

    }

    @Override
    public boolean onTouchEvent(MotionEvent e)
    {
        boolean b = scaleGestureDetector.onTouchEvent(e);
        boolean c = gestureDetector.onTouchEvent(e);
        return b || c || super.onTouchEvent(e);
    }


    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then sending the request.
     */
    private void requestCameraPermission()
    {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA))
        {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;
        View.OnClickListener listener = new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                ActivityCompat.requestPermissions(thisActivity, permissions, RC_HANDLE_CAMERA_PERM);
            }
        };

        DialogInterface.OnClickListener dListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id)
            {
                finish();
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Barcode Buddy")
                .setMessage(R.string.permission_camera_rationale)
                .setPositiveButton(R.string.ok, dListener)
                .show();
    }


    private void createCameraSource(boolean autoFocusOn, boolean flashOn)
    {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(getApplicationContext())
                .setBarcodeFormats(Barcode.CODE_128 | Barcode.QR_CODE)
                .build();

        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(overlay);
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational())
        {
            // Check for low storage.  If there is low storage, the native library will not be

            // downloaded, so detection will not become operational.

            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;



            if (hasLowStorage) {

                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();

                Log.w(TAG, getString(R.string.low_storage_error));

            }
            Log.w(TAG, "Detector dependencies are not yet available.");

        }

//        cameraSource = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
//                .setFacing(CameraSource.CAMERA_FACING_BACK)
//                .setAutoFocusEnabled(true)
//                .setRequestedPreviewSize(1600, 1024)
//                .setRequestedFps(15.0f)
//                .build();

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        // make sure that auto focus is an available option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        {
            builder = builder.setFocusMode(autoFocusOn ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
        }

        cameraSource = builder
                .setFlashMode(flashOn ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();




        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.


//
//                barcodeDetector.setProcessor(new Detector.Processor<Barcode>()
//                {
//                    @Override
//                    public void release() {
//
//                    }
//
//                    @Override
//                    public void receiveDetections(Detector.Detections<Barcode> detections) {
//                        final SparseArray<Barcode> barcodes = detections.getDetectedItems();
//
////                        if (barcodes.size() != 0) {
////                            scanResult.post(new Runnable() {    // Use the post method of the TextView
////                                public void run() {
////                                    scanResult.setText(barcodes.valueAt(0).displayValue);
////                                    Log.d(TAG, barcodes.valueAt(0).displayValue);
////                                }
////                            });
////                        }
//                    }
//                });


    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException
    {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
        if (code != ConnectionResult.SUCCESS)
        {
            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (cameraSource != null) {
            try {
                preview.start(cameraSource, overlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }


    }

    /**
     * onTap is called to capture the oldest barcode currently detected and
     * return it to the caller.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the activity is ending.
     */
    private boolean onTap(float rawX, float rawY) {

        //TODO: use the tap position to select the barcode.
        BarcodeGraphic graphic = overlay.getFirstGraphic();
        Barcode barcode = null;
        if (graphic != null)
        {
            barcode = graphic.getBarcode();
            if (barcode != null)
            {
                scanResult.setText(barcode.displayValue);
            }
            else
            {
                Log.d(TAG, "barcode data is null");
            }
        }
        else
        {
            Log.d(TAG,"no barcode detected");
        }
        return barcode != null;
    }





    // Restart the camera
    @Override
    protected void onResume()
    {
        super.onResume();
        startCameraSource();
    }

    // Stop the camera
    @Override
    protected void onPause()
    {
        super.onPause();
        if (preview != null)
        {
            preview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (preview != null)
        {
            preview.release();
        }


    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");

//          we have permission, so create the camerasource

            createCameraSource(useAutoFocus, useFlash);
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Barcode Buddy")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener
    {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e)
        {

            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector)
        {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector)
        {
            return true;
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing
         * pointers going up.
         * <p/>
         * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
         * and {@link ScaleGestureDetector#getFocusY()} will return focal point
         * of the pointers remaining on the screen.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector)
        {
            cameraSource.doZoom(detector.getScaleFactor());
        }
    }

    public void toggleFlash()
    {
        Intent intent = new Intent(MainActivity.this,  MainActivity.class);
        Bundle bundle = new Bundle();
        bundle.putBoolean("Flash", useFlash);
        bundle.putBoolean("AutoFocus", useAutoFocus);
        intent.putExtras(bundle);
        MainActivity.this.finish();
        startActivity(intent);

//        createCameraSource(useAutoFocus, useFlash);
    }


}
