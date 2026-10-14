package com.accessdroid.termux;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class MediaProjectionService extends Service {

    private static final String TAG = "AccessDroidMediaProj";
    private MediaProjectionManager mgr;
    private MediaProjection projection;
    private VirtualDisplay vd;
    private ImageReader reader;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int screenW, screenH;
    private int dpi;

    public static final String ACTION_START = "com.accessdroid.termux.ACTION_START_PROJECTION";
    public static final String ACTION_STOP  = "com.accessdroid.termux.ACTION_STOP_PROJECTION";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_DATA = "data";

    @Override
    public void onCreate() {
        super.onCreate();
        mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        dpi = dm.densityDpi;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            if (data != null && code == android.app.Activity.RESULT_OK) {
                startProjection(code, data);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopProjection();
        }
        return START_STICKY;
    }

    private void startProjection(int code, Intent data) {
        projection = mgr.getMediaProjection(code, data);
        reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2);
        vd = projection.createVirtualDisplay("AccessDroidScreen",
            screenW, screenH, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.getSurface(), null, handler);

        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override public void onImageAvailable(ImageReader r) { onImageAvailable(r); }
        }, handler);
        Log.i(TAG, "MediaProjection started " + screenW + "x" + screenH);
    }

    private void onImageAvailable(ImageReader r) {
        Image img = r.acquireLatestImage();
        if (img == null) return;
        try {
            Image.Plane[] planes = img.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenW;
            Bitmap bmp = Bitmap.createBitmap(screenW + rowPadding / pixelStride, screenH, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(bmp, 0, 0, screenW, screenH);
            saveBitmap(cropped);
            cropped.recycle();
            bmp.recycle();
        } finally {
            img.close();
        }
    }

    private void saveBitmap(Bitmap bmp) {
        try {
            String path = "/sdcard/accessdroid_screen.png";
            FileOutputStream fos = new FileOutputStream(path);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Log.i(TAG, "Screenshot saved to " + path);
        } catch (Exception e) {
            Log.e(TAG, "save failed", e);
        }
    }

    private void stopProjection() {
        if (vd != null) { vd.release(); vd = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { projection.stop(); projection = null; }
        Log.i(TAG, "MediaProjection stopped");
    }

    @Override
    public void onDestroy() {
        stopProjection();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}