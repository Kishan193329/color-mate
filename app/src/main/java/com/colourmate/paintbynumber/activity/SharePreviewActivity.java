package com.colourmate.paintbynumber.activity;

import static android.view.View.GONE;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.ads.MediationManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("ALL")
public class SharePreviewActivity extends BaseActivity {

    public static final String APPFOLDER = "ColorBook";
    public static final String TAG = "Share";

    private static final String PKG_MESSENGER = "com.facebook.orca";
    private static final String PKG_WHATSAPP = "com.whatsapp";
    private static final String PKG_INSTAGRAM = "com.instagram.android";

    private ImageView btnMessenger, btnSms, btnWhatsapp, btnInstagram, btnMore;

    // -------- Legacy permission launcher (Android 9 and below only) --------
    private final ActivityResultLauncher<String[]> legacyPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (Boolean b : result.values()) {
                    if (b == null || !b) {
                        granted = false;
                        break;
                    }
                }
                if (granted) saveToGalleryLegacy();
                else showLegacyPermissionMessage();
            });

    // -------- Share launcher --------
    private final ActivityResultLauncher<Intent> shareLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    r -> Log.d(TAG, "Share result code: " + r.getResultCode()));

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_share);
        CompatUtils.setupStatusBar(this, true, Color.WHITE);
        MediationManager.getNativeAd(this, false);
        setupToolbar();
        bindViews();
        setupClicks();

        setImageViewAndPrepareFile();
    }

    private void setupToolbar() {

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithAnim();
            }
        });


        TextView tvDone = findViewById(R.id.tv_done);
        tvDone.setVisibility(GONE);
        // ✅ IMPORTANT: in your XML iv_back is RelativeLayout, not LinearLayout
        View back = findViewById(R.id.iv_back);

        tvDone.setOnClickListener(v -> finishWithAnim());
        back.setOnClickListener(v -> finishWithAnim());
    }

    private void finishWithAnim() {

        MediationManager.showBackInterstitial(this, () -> {
            finish();
            overridePendingTransition(R.anim.slide_from_left, R.anim.slide_to_right);
        });
    }

    private void bindViews() {
        btnMessenger = findViewById(R.id.ib_fb_messenger);
        btnSms = findViewById(R.id.ib_sms);
        btnWhatsapp = findViewById(R.id.ib_whatsapp);
        btnInstagram = findViewById(R.id.ib_instagram);
        btnMore = findViewById(R.id.ib_openin);

        // ✅ Always visible (you want all icons show)
        btnMessenger.setVisibility(View.VISIBLE);
        btnSms.setVisibility(View.VISIBLE);
        btnWhatsapp.setVisibility(View.VISIBLE);
        btnInstagram.setVisibility(View.VISIBLE);
        btnMore.setVisibility(View.VISIBLE);
    }

    private void setupClicks() {
        btnMessenger.setOnClickListener(v -> shareToAppOrStore(PKG_MESSENGER));
        btnWhatsapp.setOnClickListener(v -> shareToAppOrStore(PKG_WHATSAPP));
        btnInstagram.setOnClickListener(v -> shareToAppOrStore(PKG_INSTAGRAM));

        // ✅ SMS: do NOT use com.android.mms (varies by device)
        btnSms.setOnClickListener(v -> shareViaSms());

        // ✅ More: chooser
        btnMore.setOnClickListener(v -> shareChooser());
    }

    // ---------------------------
    // Preview + prepare file
    // ---------------------------
    private void setImageViewAndPrepareFile() {
        ImageView shareImageView = findViewById(R.id.iv_share_image);

        File shareFile = getShareImageFile();
        if (shareFile == null || !shareFile.exists()) {
            Toast.makeText(this, "Share image not found", Toast.LENGTH_SHORT).show();
            return;
        }

        loadImageWithRetry(shareImageView, shareFile.getAbsolutePath());

        // ✅ Save to gallery
        saveToGallerySmart();
    }

    private void loadImageWithRetry(ImageView imageView, String imagePath) {
        final int MAX_RETRY = 10;
        final int DELAY_MS = 250;

        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            int attempts = 0;

            @Override
            public void run() {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }
                attempts++;
                if (attempts < MAX_RETRY) handler.postDelayed(this, DELAY_MS);
            }
        }, 150);
    }

    private File getShareImageFile() {
        return new File(GlobalApplication.getShareDirectory(), "recolor.jpg");
    }

    // ---------------------------
    // Share (ALL working)
    // ---------------------------
    private void shareChooser() {
        Intent intent = buildShareIntent();
        if (intent == null) return;
        shareLauncher.launch(Intent.createChooser(intent, getString(R.string.share_dialog_title)));
    }

    private void shareToAppOrStore(String pkg) {
        if (!isPackageInstalled(pkg)) {
            openPlayStore(pkg);
            return;
        }
        Intent intent = buildShareIntent();
        if (intent == null) return;
        intent.setPackage(pkg);

        try {
            shareLauncher.launch(intent);
        } catch (Exception e) {
            // fallback chooser
            shareChooser();
        }
    }

    private void shareViaSms() {
        Uri uri = getShareFileUri();
        if (uri == null) return;

        // Many SMS apps don't accept EXTRA_STREAM with SENDTO,
        // so best approach: regular chooser but title says "SMS"
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        shareLauncher.launch(Intent.createChooser(intent, "Send via SMS"));
    }

    private Intent buildShareIntent() {
        Uri uri = getShareFileUri();
        if (uri == null) return null;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/jpeg");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return shareIntent;
    }

    private Uri getShareFileUri() {
        File shareImage = getShareImageFile();
        if (shareImage == null || !shareImage.exists()) {
            Toast.makeText(this, "Share file missing", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            return FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    shareImage
            );
        } catch (Exception e) {
            Log.e(TAG, "FileProvider error", e);
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
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (Exception e) {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------
    // Save to Gallery (LATEST SAFE)
    // ---------------------------
    private void saveToGallerySmart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToGalleryModern();
            return;
        }

        // Android 9 and below needs permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            saveToGalleryLegacy();
        } else {
            legacyPermissionLauncher.launch(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    // Android 10+ : MediaStore (no permission needed)
    private void saveToGalleryModern() {
        File input = getShareImageFile();
        if (input == null || !input.exists()) return;

        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date()) + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + APPFOLDER);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return;

        try (OutputStream out = resolver.openOutputStream(uri);
             FileInputStream in = new FileInputStream(input)) {

            if (out == null) return;
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            out.flush();

        } catch (Exception e) {
            Log.e(TAG, "saveToGalleryModern error", e);
        }

        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
    }

    // Android 9 and below : Pictures/ColorBook (needs permission)
    private void saveToGalleryLegacy() {
        File input = getShareImageFile();
        if (input == null || !input.exists()) return;

        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                APPFOLDER
        );
        if (!dir.exists()) dir.mkdirs();

        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date()) + ".jpg";

        File outFile = new File(dir, fileName);

        try (FileInputStream in = new FileInputStream(input);
             FileOutputStream out = new FileOutputStream(outFile)) {

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            out.flush();

            // add to gallery index
            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scanIntent.setData(Uri.fromFile(outFile));
            sendBroadcast(scanIntent);

        } catch (IOException e) {
            Log.e(TAG, "saveToGalleryLegacy error", e);
        }
    }

    private void showLegacyPermissionMessage() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.save_as_image_text)
                .setMessage("Storage permission denied. Sharing will work, but saving may fail on older Android.")
                .setNeutralButton(R.string.dialog_ok, null)
                .show();
    }
}


