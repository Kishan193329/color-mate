package com.colourmate.paintbynumber.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import com.colourmate.paintbynumber.databinding.ActivityShareBinding;
import com.colourmate.paintbynumber.util.LogUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.util.AchievementManager;
import com.colourmate.paintbynumber.util.TimeLapseRecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("ALL")
public class ArtworkShareActivity extends BaseActivity {

    private com.colourmate.paintbynumber.databinding.ActivityShareBinding binding;
    private Bitmap previewBitmap;

    public static final String TAG = "Share";
    private static final int REQ_STORAGE = 1001;

    // Quick share package names
    private static final String PKG_MESSENGER = "com.facebook.orca";
    private static final String PKG_WHATSAPP = "com.whatsapp";
    private static final String PKG_INSTAGRAM = "com.instagram.android";

    private String imagePath; // absolute path

    // -------- Modern Share launcher --------
    private final ActivityResultLauncher<Intent> shareLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result ->
                    LogUtils.d(TAG, "Share result = " + result.getResultCode())
            );

    // -------- Legacy storage permission launcher (Android 9 and below) --------
    private final ActivityResultLauncher<String[]> legacyPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (Boolean b : result.values()) {
                    if (b == null || !b) {
                        granted = false;
                        break;
                    }
                }
                if (granted) {
                    saveImageToGalleryLegacy();
                } else {
                    showLegacyPermissionMessage();
                }
            });

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        CompatUtils.setupStatusBar(this, true, Color.WHITE);

        MediationManager.getNativeAd(this, false);

        // ✅ Back gesture support
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {

                MediationManager.showBackInterstitial(ArtworkShareActivity.this, () -> {
                    finishWithAnim();
                });
            }
        });

        imagePath = getIntent().getStringExtra("path");
        String beforePath = getIntent().getStringExtra("before_path");
        String levelKey = getIntent().getStringExtra("level_key");
        
        if (imagePath == null || imagePath.trim().isEmpty()) {
            finishWithAnim();
            return;
        }

//        setupBeforeAfterButton(beforePath);

        File f = new File(imagePath);
        if (!f.exists()) {
            Toast.makeText(this, R.string.error_image_not_found, Toast.LENGTH_SHORT).show();
            finishWithAnim();
            return;
        }

        setupToolbar();
        setupPreviewImage();
        setupShareButtons();  // ✅ icons always visible + click logic

        // ✅ Save to Gallery once on open
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToGalleryScoped();
        } else {
            if (!isLegacyStoragePermissionGranted()) {
                requestLegacyStoragePermission();
            } else {
                saveImageToGalleryLegacy();
            }
        }

        // your original broadcast
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("numberreload"));
    }

    // ---------------- Toolbar ----------------
    private void setupToolbar() {
        binding.tvDone.setOnClickListener(v -> {
            MediationManager.showInterstitial(this, () -> {
                Intent intent = new Intent(ArtworkShareActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
            });
        });

        binding.ivBack.setOnClickListener(v -> {
            MediationManager.showBackInterstitial(this, () -> {
                finishWithAnim();
            });
        });
    }

    // ---------------- Preview Image ----------------
    private void setupPreviewImage() {
        new Thread(() -> {
            Bitmap bmp = null;
            int retries = 5;
            while (bmp == null && retries > 0) {
                try {
                    bmp = BitmapFactory.decodeFile(imagePath);
                    if (bmp == null) {
                        Thread.sleep(200);
                        retries--;
                    }
                } catch (Exception ignored) {}
            }

            final Bitmap finalBmp = bmp;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (finalBmp != null) finalBmp.recycle();
                    return;
                }
                if (finalBmp != null) {
                    previewBitmap = finalBmp;
                    binding.ivShareImage.setImageBitmap(previewBitmap);
                }
            });
        }).start();
    }

    // ---------------- Share Buttons (always visible) ----------------
    private void setupShareButtons() {
        // ✅ Always visible (no hide logic)
        binding.ibFbMessenger.setVisibility(View.VISIBLE);
        binding.ibSms.setVisibility(View.VISIBLE);
        binding.ibWhatsapp.setVisibility(View.VISIBLE);
        binding.ibInstagram.setVisibility(View.VISIBLE);
        binding.ibOpenin.setVisibility(View.VISIBLE);

        binding.ibFbMessenger.setOnClickListener(v -> shareToSpecificApp(PKG_MESSENGER));
        binding.ibWhatsapp.setOnClickListener(v -> shareToSpecificApp(PKG_WHATSAPP));
        binding.ibInstagram.setOnClickListener(v -> shareToSpecificApp(PKG_INSTAGRAM));

        // ✅ SMS is NOT one fixed package on all devices → use ACTION_SENDTO
        binding.ibSms.setOnClickListener(v -> shareViaSms());

        // ✅ More = system chooser
        binding.ibOpenin.setOnClickListener(v -> shareChooser());
    }

    // ---------------- Share helpers ----------------
    private void shareChooser() {
        Intent share = buildShareIntent();
        if (share == null) return;
        AchievementManager.recordShare(this);
        shareLauncher.launch(Intent.createChooser(share, getString(R.string.share_dialog_title)));
    }

    private void shareToSpecificApp(String packageName) {
        if (!isPackageInstalled(packageName)) {
            openPlayStore(packageName);
            return;
        }

        Intent share = buildShareIntent();
        if (share == null) return;

        share.setPackage(packageName);
        AchievementManager.recordShare(this);
        try {
            shareLauncher.launch(share);
        } catch (Exception e) {
            shareChooser();
        }
    }

    private void shareViaSms() {
        Uri uri = getFileUriSafe();
        if (uri == null) return;

        // SMS apps accept text or stream depending on app; safest is chooser for SEND with stream.
        // Many SMS apps don't accept image stream in SENDTO, so we use normal share chooser,
        // but with a hint "sms:" fallback.
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Prefer SMS handlers if present (best-effort)
        List<ResolveInfo> smsApps = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                0
        );

        if (smsApps != null && !smsApps.isEmpty()) {
            // If device has a default SMS app, you could set package to that.
            // But different vendors → keep chooser
            shareLauncher.launch(Intent.createChooser(intent, "Send via SMS"));
        } else {
            shareLauncher.launch(Intent.createChooser(intent, getString(R.string.share_dialog_title)));
        }
    }

    private Intent buildShareIntent() {
        Uri uri = getFileUriSafe();
        if (uri == null) return null;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private Uri getFileUriSafe() {
        File file = new File(imagePath);
        if (!file.exists()) {
            Toast.makeText(this, "File missing", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            return FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );
        } catch (Exception e) {
            LogUtils.e(TAG, "FileProvider error", e);
            Toast.makeText(this, "Sharing failed", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openPlayStore(String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- Save To Gallery ----------------

    // Android 10+ (Scoped Storage)
    private void saveImageToGalleryScoped() {
        try {
            File src = new File(imagePath);
            if (!src.exists()) return;

            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "IMG_" + time + ".jpg";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + "ColorBook");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            ContentResolver resolver = getContentResolver();
            Uri item = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (item == null) return;

            try (OutputStream out = resolver.openOutputStream(item);
                 FileInputStream in = new FileInputStream(src)) {

                if (out == null) return;

                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                out.flush();
            }

            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(item, values, null, null);

        } catch (Exception e) {
            LogUtils.e(TAG, "saveImageToGalleryScoped error", e);
        }
    }

    // Android 9 and below (Legacy)
    private void saveImageToGalleryLegacy() {
        try {
            File src = new File(imagePath);
            if (!src.exists()) return;

            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "ColorBook"
            );
            if (!dir.exists()) dir.mkdirs();

            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dest = new File(dir, "IMG_" + time + ".jpg");

            try (FileInputStream in = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dest)) {

                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                out.flush();
            }

            Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scan.setData(Uri.fromFile(dest));
            sendBroadcast(scan);

        } catch (Exception e) {
            LogUtils.e(TAG, "saveImageToGalleryLegacy error", e);
        }
    }

    // ---------------- Permission (Legacy only) ----------------
    private boolean isLegacyStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLegacyStoragePermission() {
        legacyPermissionLauncher.launch(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        });
    }

    private void showLegacyPermissionMessage() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.save_as_image_text)
                .setMessage("Storage permission denied. You can still share, but saving to gallery may not work on older Android.")
                .setNeutralButton(R.string.dialog_ok, null)
                .show();
    }

    private void finishWithAnim() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) {
            previewBitmap.recycle();
        }
        finish();
        overridePendingTransition(R.anim.slide_from_left, R.anim.slide_to_right);
    }

    @Override
    protected void onDestroy() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) {
            previewBitmap.recycle();
        }
        super.onDestroy();
    }
}


