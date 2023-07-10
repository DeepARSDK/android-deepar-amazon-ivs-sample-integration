package ai.deepar.deepar_example;

import ai.deepar.ar.ARErrorType;
import ai.deepar.ar.AREventListener;
import ai.deepar.ar.CameraResolutionPreset;
import ai.deepar.ar.DeepAR;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.ivs.broadcast.BroadcastConfiguration;
import com.amazonaws.ivs.broadcast.BroadcastException;
import com.amazonaws.ivs.broadcast.BroadcastSession;
import com.amazonaws.ivs.broadcast.Device;
import com.amazonaws.ivs.broadcast.ImageDevice;
import com.amazonaws.ivs.broadcast.Presets;
import com.amazonaws.ivs.broadcast.SurfaceSource;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;
import ai.deepar.ar.DeepARImageFormat;

public class MainActivity extends AppCompatActivity implements AREventListener {

    // Default camera lens value, change to CameraSelector.LENS_FACING_BACK to initialize with back camera
    private int defaultLensFacing = CameraSelector.LENS_FACING_FRONT;
    private ARSurfaceProvider surfaceProvider = null;
    private int lensFacing = defaultLensFacing;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ByteBuffer[] buffers;
    private int currentBuffer = 0;
    private static final int NUMBER_OF_BUFFERS=2;
    private static final boolean useExternalCameraTexture = true;

    private DeepAR deepAR;

    private int currentMask=0;
    private int currentEffect=0;
    private int currentFilter=0;

    private int screenOrientation;

    ArrayList<String> masks;
    ArrayList<String> effects;
    ArrayList<String> filters;

    private int activeFilterType = 0;

    private int width = 0;
    private int height = 0;

    // Amazon IVS
    private BroadcastSession.Listener broadcastListener;
    private BroadcastSession broadcastSession;
    private BroadcastConfiguration broadcastConfig;
    private Device.Descriptor microphone;
    private SurfaceSource surfaceSource;
    private Surface surface;
    private boolean streamRunning = false;
    private final String INGEST_SERVER = "copy_from_ivs_console";
    private final String STREAM_KEY = "copy_from_ivs_console";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO },
                    1);
        } else {
            // Permission has already been granted
            initialize();
        }

        super.onStart();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  String[] permissions, int[] grantResults) {
        if (requestCode == 1 && grantResults.length > 0) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    return; // no permission
                }
            }
            initialize();
        }
    }

    private void initialize() {
        initializeDeepAR();
        initializeFilters();
        initalizeViews();
    }

    private void initializeFilters() {
        masks = new ArrayList<>();
        masks.add("none");
        masks.add("aviators");
        masks.add("bigmouth");
        masks.add("dalmatian");
        masks.add("flowers");
        masks.add("koala");
        masks.add("lion");
        masks.add("smallface");
        masks.add("teddycigar");
        masks.add("background_segmentation");
        masks.add("tripleface");
        masks.add("sleepingmask");
        masks.add("fatify");
        masks.add("mudmask");
        masks.add("pug");
        masks.add("twistedface");
        masks.add("grumpycat");
        masks.add("Helmet_PBR_V1");

        effects = new ArrayList<>();
        effects.add("none");
        effects.add("fire");
        effects.add("rain");
        effects.add("heart");
        effects.add("blizzard");

        filters = new ArrayList<>();
        filters.add("none");
        filters.add("filmcolorperfection");
        filters.add("tv80");
        filters.add("drawingmanga");
        filters.add("sepia");
        filters.add("bleachbypass");
    }

    private void initalizeViews() {
        ImageButton previousMask = findViewById(R.id.previousMask);
        ImageButton nextMask = findViewById(R.id.nextMask);

        final RadioButton radioMasks = findViewById(R.id.masks);
        final RadioButton radioEffects = findViewById(R.id.effects);
        final RadioButton radioFilters = findViewById(R.id.filters);

        ImageButton switchCamera = findViewById(R.id.switchCamera);
        switchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lensFacing = lensFacing ==  CameraSelector.LENS_FACING_FRONT ?  CameraSelector.LENS_FACING_BACK :  CameraSelector.LENS_FACING_FRONT ;
                //unbind immediately to avoid mirrored frame.
                ProcessCameraProvider cameraProvider = null;
                try {
                    cameraProvider = cameraProviderFuture.get();
                    cameraProvider.unbindAll();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                setupCamera();

//                if(currentCamera.equals(frontCamera)) {
//                    broadcastSession.exchangeDevices(currentCamera, rearCamera, camera -> {
//                        currentCamera = camera.getDescriptor();
//                    });
//                }
//                else {
//                    broadcastSession.exchangeDevices(currentCamera, frontCamera, camera -> {
//                        currentCamera = camera.getDescriptor();
//                    });
//                }
            }
        });

        ImageButton openActivity = findViewById(R.id.openActivity);
        openActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this, BasicActivity.class);
                MainActivity.this.startActivity(myIntent);
            }


        });

        previousMask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gotoPrevious();
            }
        });

        nextMask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gotoNext();
            }
        });

        radioMasks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                radioEffects.setChecked(false);
                radioFilters.setChecked(false);
                activeFilterType = 0;
            }
        });
        radioEffects.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                radioMasks.setChecked(false);
                radioFilters.setChecked(false);
                activeFilterType = 1;
            }
        });
        radioFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                radioEffects.setChecked(false);
                radioMasks.setChecked(false);
                activeFilterType = 2;
            }
        });
    }
    /*
            get interface orientation from
            https://stackoverflow.com/questions/10380989/how-do-i-get-the-current-orientation-activityinfo-screen-orientation-of-an-a/10383164
         */
    private int getScreenOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        width = dm.widthPixels;
        height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }
    private void initializeDeepAR() {
        deepAR = new DeepAR(this);
        deepAR.setLicenseKey("your_licence_key_here");      // **************************** YOUR LICENCE KEY GOES HERE ****************************
        deepAR.initialize(this, this);
        setupIVS();
        setupCamera();
    }

    private void setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindImageAnalysis(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        CameraResolutionPreset cameraResolutionPreset = CameraResolutionPreset.P1280x720;
        int width;
        int height;
        int orientation = getScreenOrientation();
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || orientation ==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
            width = cameraResolutionPreset.getWidth();
            height =  cameraResolutionPreset.getHeight();
        } else {
            width = cameraResolutionPreset.getHeight();
            height = cameraResolutionPreset.getWidth();
        }

        Size cameraResolution = new Size(width, height);
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        if(useExternalCameraTexture) {
            Preview preview = new Preview.Builder()
                    .setTargetResolution(cameraResolution)
                    .build();

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview);
            if(surfaceProvider == null) {
                surfaceProvider = new ARSurfaceProvider(this, deepAR);
            }
            preview.setSurfaceProvider(surfaceProvider);
            surfaceProvider.setMirror(lensFacing == CameraSelector.LENS_FACING_FRONT);
        } else {
            buffers = new ByteBuffer[NUMBER_OF_BUFFERS];
            for (int i = 0; i < NUMBER_OF_BUFFERS; i++) {
                buffers[i] = ByteBuffer.allocateDirect(width * height * 3);
                buffers[i].order(ByteOrder.nativeOrder());
                buffers[i].position(0);
            }
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(cameraResolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer);
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis);
        }
    }

    private ImageAnalysis.Analyzer imageAnalyzer = new ImageAnalysis.Analyzer() {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            byte[] byteData;
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byteData = new byte[ySize + uSize + vSize];

            int width = image.getWidth();
            int yStride = image.getPlanes()[0].getRowStride();
            int uStride = image.getPlanes()[1].getRowStride();
            int vStride = image.getPlanes()[2].getRowStride();
            int outputOffset = 0;
            if (width == yStride) {
                yBuffer.get(byteData, outputOffset, ySize);
                outputOffset += ySize;
            } else {
                for (int inputOffset = 0; inputOffset < ySize; inputOffset += yStride) {
                    yBuffer.position(inputOffset);
                    yBuffer.get(byteData, outputOffset, Math.min(yBuffer.remaining(), width));
                    outputOffset += width;
                }
            }
            //U and V are swapped
            if (width == vStride) {
                vBuffer.get(byteData, outputOffset, vSize);
                outputOffset += vSize;
            } else {
                for (int inputOffset = 0; inputOffset < vSize; inputOffset += vStride) {
                    vBuffer.position(inputOffset);
                    vBuffer.get(byteData, outputOffset, Math.min(vBuffer.remaining(), width));
                    outputOffset += width;
                }
            }
            if (width == uStride) {
                uBuffer.get(byteData, outputOffset, uSize);
                outputOffset += uSize;
            } else {
                for (int inputOffset = 0; inputOffset < uSize; inputOffset += uStride) {
                    uBuffer.position(inputOffset);
                    uBuffer.get(byteData, outputOffset, Math.min(uBuffer.remaining(), width));
                    outputOffset += width;
                }
            }

            buffers[currentBuffer].put(byteData);
            buffers[currentBuffer].position(0);
            if (deepAR != null) {
                deepAR.receiveFrame(buffers[currentBuffer],
                        image.getWidth(), image.getHeight(),
                        image.getImageInfo().getRotationDegrees(),
                        lensFacing == CameraSelector.LENS_FACING_FRONT,
                        DeepARImageFormat.YUV_420_888,
                        image.getPlanes()[1].getPixelStride()
                );
            }
            currentBuffer = (currentBuffer + 1) % NUMBER_OF_BUFFERS;
            image.close();
        }
    };


    private String getFilterPath(String filterName) {
        if (filterName.equals("none")) {
            return null;
        }
        return "file:///android_asset/" + filterName;
    }

    private void gotoNext() {
        if (activeFilterType == 0) {
            currentMask = (currentMask + 1) % masks.size();
            deepAR.switchEffect("mask", getFilterPath(masks.get(currentMask)));
        } else if (activeFilterType == 1) {
            currentEffect = (currentEffect + 1) % effects.size();
            deepAR.switchEffect("effect", getFilterPath(effects.get(currentEffect)));
        } else if (activeFilterType == 2) {
            currentFilter = (currentFilter + 1) % filters.size();
            deepAR.switchEffect("filter", getFilterPath(filters.get(currentFilter)));
        }
    }

    private void gotoPrevious() {
        if (activeFilterType == 0) {
            currentMask = (currentMask - 1 + masks.size()) % masks.size();
            deepAR.switchEffect("mask", getFilterPath(masks.get(currentMask)));
        } else if (activeFilterType == 1) {
            currentEffect = (currentEffect - 1 + effects.size()) % effects.size();
            deepAR.switchEffect("effect", getFilterPath(effects.get(currentEffect)));
        } else if (activeFilterType == 2) {
            currentFilter = (currentFilter - 1 + filters.size()) % filters.size();
            deepAR.switchEffect("filter", getFilterPath(filters.get(currentFilter)));
        }
    }

    @Override
    protected void onStop() {
        ProcessCameraProvider cameraProvider = null;
        try {
            cameraProvider = cameraProviderFuture.get();
            cameraProvider.unbindAll();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(surfaceProvider != null) {
            surfaceProvider.stop();
            surfaceProvider = null;
        }
        deepAR.release();
        deepAR = null;

        if(streamRunning) {
            broadcastSession.stop();
            streamRunning = false;
        }
        broadcastSession.release();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(surfaceProvider != null) {
            surfaceProvider.stop();
        }
        if (deepAR == null) {
            return;
        }
        deepAR.setAREventListener(null);
        deepAR.release();
        deepAR = null;

        if(streamRunning) {
            broadcastSession.stop();
            streamRunning = false;
        }
        broadcastSession.release();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void setupIVS() {
        broadcastListener = new BroadcastSession.Listener() {
            @Override
            public void onStateChanged(@NonNull BroadcastSession.State state) {
                android.util.Log.d("Amazon IVS", "State = " + state);
            }

            @Override
            public void onError(@NonNull BroadcastException e) {
                android.util.Log.d("Amazon IVS", "Exception = " + e);
            }
        };

        int streamingWidth = 720;
        int streamingHeight = 1280;

        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            streamingWidth = 1280;
            streamingHeight = 720;
        }

        int finalStreamingWidth = streamingWidth;
        int finalStreamingHeight = streamingHeight;
        broadcastConfig = BroadcastConfiguration.with($ -> {
            $.video.setSize(finalStreamingWidth, finalStreamingHeight);
            $.mixer.slots = new BroadcastConfiguration.Mixer.Slot[] {
              BroadcastConfiguration.Mixer.Slot.with(slot -> {
                  slot.setPreferredVideoInput(Device.Descriptor.DeviceType.USER_IMAGE);
                  slot.setPreferredAudioInput(Device.Descriptor.DeviceType.MICROPHONE);
                  slot.setName("custom");
                  return slot;
              })
            };
            return $;
        });


        broadcastSession = new BroadcastSession(this, broadcastListener, broadcastConfig, Presets.Devices.MICROPHONE(this));

        surfaceSource = broadcastSession.createImageInputSource();
        if(!surfaceSource.isValid()) {
            throw new IllegalStateException("Amazon IVS surface not valid!");
        }
        surfaceSource.setSize(streamingWidth, streamingHeight);
        surfaceSource.setRotation(ImageDevice.Rotation.ROTATION_0);
        surface = surfaceSource.getInputSurface();

//        BroadcastConfiguration.Mixer.Slot slot = new BroadcastConfiguration.Mixer.Slot();
//        broadcastSession.getMixer().addSlot()

        boolean success = broadcastSession.getMixer().bind(surfaceSource, "custom");

        deepAR.setRenderSurface(surface, streamingWidth, streamingHeight);

        TextureView view = broadcastSession.getPreviewView(BroadcastConfiguration.AspectMode.FIT);

        ConstraintLayout layout = (ConstraintLayout) findViewById(R.id.rootLayout);
        layout.addView(view, 0);

        broadcastSession.start(INGEST_SERVER, STREAM_KEY);
        streamRunning = true;
    }

    @Override
    public void screenshotTaken(Bitmap bitmap) {
        CharSequence now = DateFormat.format("yyyy_MM_dd_hh_mm_ss", new Date());
        try {
            File imageFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "image_" + now + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();
            MediaScannerConnection.scanFile(MainActivity.this, new String[]{imageFile.toString()}, null, null);
            Toast.makeText(MainActivity.this, "Screenshot " + imageFile.getName() + " saved.", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    @Override
    public void videoRecordingStarted() {

    }

    @Override
    public void videoRecordingFinished() {

    }

    @Override
    public void videoRecordingFailed() {

    }

    @Override
    public void videoRecordingPrepared() {

    }

    @Override
    public void shutdownFinished() {

    }

    @Override
    public void initialized() {
        // Restore effect state after deepar release
        deepAR.switchEffect("mask", getFilterPath(masks.get(currentMask)));
        deepAR.switchEffect("effect", getFilterPath(effects.get(currentEffect)));
        deepAR.switchEffect("filter", getFilterPath(filters.get(currentFilter)));
    }

    @Override
    public void faceVisibilityChanged(boolean b) {

    }

    @Override
    public void imageVisibilityChanged(String s, boolean b) {

    }

    @Override
    public void frameAvailable(Image frame) {
//        if(frame != null) {
//            final Image.Plane[] planes = frame.getPlanes();
//            final Buffer buffer = planes[0].getBuffer().rewind();
//            int stride = planes[0].getPixelStride();
//            int rowStride = planes[0].getRowStride();
//            int rowPadding = rowStride - stride * frame.getWidth();
//            Bitmap bitmap = Bitmap.createBitmap(frame.getWidth() + rowPadding / stride, frame.getHeight(), Bitmap.Config.ARGB_8888);
//            bitmap.copyPixelsFromBuffer(buffer);
//            offscreenImage.setImageBitmap(bitmap);
//        }
    }

    @Override
    public void error(ARErrorType arErrorType, String s) {

    }


    @Override
    public void effectSwitched(String s) {

    }
}
