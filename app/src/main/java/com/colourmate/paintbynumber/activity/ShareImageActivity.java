package com.colourmate.paintbynumber.activity;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.Utils.ImageManager;
import com.colourmate.paintbynumber.Utils.Utils;
import com.colourmate.paintbynumber.ads.MediationManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.List;

import pl.droidsonroids.gif.GifDrawable;

@SuppressWarnings("ALL")
public class ShareImageActivity extends AppCompatActivity {

    private AnimationDrawable animation;
    private String imageExportPath;
    private String imageID;
    private int optionExpected = 0;

    private static final int REQ_STORAGE = 1001;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        GlobalApplication.lockPortrait(this);
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
        setContentView(R.layout.activity_pixels_share);
        CompatUtils.setupStatusBar(this, true, Color.WHITE);

        MediationManager.getNativeAd(this, false);


        // ✅ Handle back gesture + back button properly
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithAnim();
            }
        });

        if (getIntent() != null) {
            imageID = getIntent().getStringExtra(PixelArtActivity.ARG_IMAGE_ID);
        }

        if (imageID == null || imageID.trim().isEmpty()) {
            finishWithAnim();
            return;
        }

        // UI
        View back = findViewById(R.id.iv_back);
        ImageView imageView = findViewById(R.id.iv_imageViewPainting);

        back.setOnClickListener(v -> finishWithAnim());

        setupTimelapseAnimation(imageView);

        // Click to replay animation
        imageView.setOnClickListener(v -> {
            if (animation != null) {
                animation.stop();
                animation.start();
            }
        });

        // Buttons
        findViewById(R.id.iv_imageViewSave).setOnClickListener(v -> {
            optionExpected = 0; // Save
            runExportFlow();
        });

        findViewById(R.id.ib_Instagram).setOnClickListener(v -> {
            optionExpected = 1; // Instagram
            runExportFlow();
        });

        findViewById(R.id.ib_Other).setOnClickListener(v -> {
            optionExpected = 2; // Other share
            runExportFlow();
        });

        findViewById(R.id.tv_done).setOnClickListener(v -> {
            // Your original behavior: save final and go back
            Bitmap bitmap;
            GifDrawable gif = ImageManager.getInstance().getGifLocal(imageID);
            if (gif != null) bitmap = gif.getCurrentFrame();
            else bitmap = ImageManager.getInstance().getImageLocal(imageID);

            if (bitmap != null) {
                ImageManager.getInstance().createAndSaveImageFile1(imageID, Utils.whiteBGImage(bitmap), ShareImageActivity.this);
            }
            finishWithAnim();
        });

        // Auto save once when opened (your original)
        optionExpected = 0;
        runExportFlow();
    }

    // ----------------------------
    // Animation setup
    // ----------------------------
    private void setupTimelapseAnimation(ImageView imageView) {
        Log.i("TAG###########", "setupTimelapseAnimation: " + imageID);
        animation = new AnimationDrawable();
        animation.setOneShot(true);

        List<Bitmap> imageArray = ImageManager.getInstance().getTimelapseArray(imageID);
        if (imageArray != null && !imageArray.isEmpty()) {
            int perFrameMs = Math.max(30, (int) (180.0f / (float) imageArray.size()));

            for (Bitmap bitmap : imageArray) {
                if (bitmap == null) continue;
                BitmapDrawable d = new BitmapDrawable(getResources(), bitmap);
                d.getPaint().setFilterBitmap(false);
                animation.addFrame(d, perFrameMs);
            }
        }

        GifDrawable gifLocal = ImageManager.getInstance().getGifLocal(imageID);
        if (gifLocal != null && gifLocal.getNumberOfFrames() > 0) {
            Bitmap first = gifLocal.seekToFrameAndGet(0);
            if (first != null) {
                BitmapDrawable d0 = new BitmapDrawable(getResources(), first);
                d0.getPaint().setFilterBitmap(false);
                animation.addFrame(d0, 75);

                int frames = gifLocal.getNumberOfFrames();
                for (int i = 0; i < frames * 4; i++) {
                    Bitmap frame = gifLocal.seekToFrameAndGet(i % frames);
                    if (frame == null) continue;
                    BitmapDrawable d = new BitmapDrawable(getResources(), frame);
                    d.getPaint().setFilterBitmap(false);
                    animation.addFrame(d, 75);
                }

                BitmapDrawable dEnd = new BitmapDrawable(getResources(), first);
                dEnd.getPaint().setFilterBitmap(false);
                animation.addFrame(dEnd, 75);
            }
        }

        // ✅ Robust Fallback System
        if (animation.getNumberOfFrames() == 0) {
            Bitmap fallback = ImageManager.getInstance().getColorImage(imageID);

            // Try progress image if color image is missing
            if (fallback == null) {
                fallback = ImageManager.getInstance().getProgressImage(imageID);
            }

            // Try original image if all else fails
            if (fallback == null) {
                fallback = ImageManager.getInstance().getImageLocal(imageID);
            }

            if (fallback != null) {
                BitmapDrawable d = new BitmapDrawable(getResources(), fallback);
                d.getPaint().setFilterBitmap(false);
                animation.addFrame(d, 3000); // Show static image for 3s
            }
        }

        imageView.setImageDrawable(animation);

        // Ensure animation starts when view is ready
        imageView.post(() -> {
            if (animation != null) {
                animation.stop();
                animation.start();
            }
        });
    }

    // ----------------------------
    // Export flow
    // ----------------------------
    private void runExportFlow() {
        // If we need legacy permission, ask, else export now
        if (needsLegacyWritePermission() && !isLegacyWritePermissionGranted()) {
            showPermissionRequestDialog();
            return;
        }
        requestImageExport();
    }

    private boolean needsLegacyWritePermission() {
        // Only Android 9 and below needs WRITE_EXTERNAL_STORAGE for direct external
        // file operations.
        // We are saving to Gallery via MediaStore, so permission not needed for Android
        // 10+.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    private boolean isLegacyWritePermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionRequestDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.save_as_image_text).setMessage(R.string.permission_denied_photos_text).setNeutralButton(R.string.button_ok, (dialog, which) -> requestLegacyPermission()).show();
    }

    private void requestLegacyPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQ_STORAGE) return;

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            requestImageExport();
        } else {
            // Don’t block user forever, just inform
            new AlertDialog.Builder(this).setTitle("Permission Needed").setMessage("Please enable Storage permission from Settings → Apps → Color by Number → Permissions.").setNeutralButton("OK", null).show();
        }
    }

    public void requestImageExport() {
        Bitmap bitmap;

        GifDrawable gif = ImageManager.getInstance().getGifLocal(imageID);
        if (gif != null) {
            bitmap = gif.getCurrentFrame();
        } else {
            // ✅ Prioritize COLORED version for sharing
            bitmap = ImageManager.getInstance().getColorImage(imageID);
            if (bitmap == null) {
                bitmap = ImageManager.getInstance().getImageLocal(imageID);
            }
        }

        if (bitmap == null) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap exportBitmap = Utils.whiteBGImage(bitmap);

        // Your existing method returns a file path
        imageExportPath = ImageManager.getInstance().createAndSaveImageFile(imageID, exportBitmap, this);

        if (imageExportPath == null) {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
            return;
        }

        if (optionExpected == 1) {
            shareToInstagram(imageExportPath);
        } else if (optionExpected == 2) {
            shareImage(imageExportPath);
        } else {
            saveImageToGallery(imageExportPath);
        }
    }

    // ----------------------------
    // Save to Gallery (Modern)
    // ----------------------------
    private void saveImageToGallery(String path) {
        boolean ok = addImageToGallery(this, path);
        Toast.makeText(this, ok ? "Saved to Gallery" : "Save failed", Toast.LENGTH_SHORT).show();
    }

    public static boolean addImageToGallery(Context context, String path) {
        File file = new File(path);
        if (!file.exists()) return false;

        try {
            String fileName = file.getName();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ColorByNumber/");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            } else {
                values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
            }

            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri == null) return false;

            try (OutputStream os = context.getContentResolver().openOutputStream(uri); FileInputStream in = new FileInputStream(file)) {

                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) os.write(buf, 0, len);
                os.flush();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);
            } else {
                // scan for old devices
                Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scan.setData(Uri.fromFile(file));
                context.sendBroadcast(scan);
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------
    // Share (Other)
    // ----------------------------
    private void shareImage(String path) {
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Share to"));
    }

    // ----------------------------
    // Share Instagram
    // ----------------------------
    private void shareToInstagram(String imagePath) {
        File file = new File(imagePath);
        if (!file.exists()) {
            Toast.makeText(this, "Image not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage("com.instagram.android");

        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if (apps != null && !apps.isEmpty()) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "Instagram app is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    // ----------------------------
    // Finish helper
    // ----------------------------
    private void finishWithAnim() {

        MediationManager.showBackInterstitial(this, () -> {
            finish();
            overridePendingTransition(R.anim.slide_from_left, R.anim.slide_to_right);
        });
    }
}
