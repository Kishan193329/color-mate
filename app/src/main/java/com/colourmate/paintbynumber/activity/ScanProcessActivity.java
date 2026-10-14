package com.colourmate.paintbynumber.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.util.AchievementManager;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.util.AssetHelper;
import com.colourmate.paintbynumber.util.EffectSelector;
import com.colourmate.paintbynumber.util.ExifHelper;
import com.colourmate.paintbynumber.util.PurchaseManager;
import com.colourmate.paintbynumber.views.ScannerGLView;
import com.colourmate.paintbynumber.views.WrapContentLinearLayoutManager;
import com.sumoing.recolor.library.LibraryItem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanProcessActivity extends BaseActivity {

    public static final int NEWPICTURESCANNED = 50001;
    public static final int RC_CAMERA = 40002;
    public static final int RC_GALLERY = 40001;
    private static final String SCAN_COUNT_KEY = "scanCount";
    private static final String SCAN_TIME_KEY = "scanTime";
    public static final String TAG = "SheetScannerActivity";
    public static LibraryItem libraryItem;
    private final Handler mCountDownHandler = new Handler();
    private boolean mCountDownRunning;
    private TextView mCounterText;
    private RelativeLayout mCropLayout;
    private int mCurrentMode;
    private LinearLayout mDescription;
    private EffectSelector.EffectAdapter mEffectAdapter;
    private RelativeLayout mEffectLayout;
    private RecyclerView mEffectRV;
    private boolean mEnableDone;
    private ScannerGLView mGlView;
    private RelativeLayout mMainPage;
    private final EffectSelector.EffectClickListener mOnEffectClickListener =
            (index, item) -> {

                // Ignore if item already selected
                if (mEffectAdapter.isSelected(index)) return;

                // Update selection
                mEffectAdapter.selectItem(index);

                // Apply effect to GL view
                mGlView.setScannerType(index);
            };
    private TextView mOpenFromGalleryBtn;
    SharedPreferences mPrefs;
    private long mPrevHour = -1;
    private ProgressBar mProgressBar;
    private LinearLayout mScannerPage;
    private boolean mScanningBusy;
    private TextView mToolbarNext;
    private TextView mToolbarTitle;
    private ImageView mUndoBtn;
    private TextView mBtnSimple, mBtnMedium, mBtnDetailed;
    private int mSelectedComplexity = 1; // 0=simple, 1=medium, 2=detailed
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_scan);
        CompatUtils.setupStatusBar(this , true, Color.WHITE);  // Light status bar with white background

        AchievementManager.recordModeTried(this, "scanner");
        initToolbar();
        initPreferences();
        initViews();
        initEffectRecycler();


        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {
                        handleBackPressed();
                    }
                }
        );

        gotoInitialMode();
        checkScanLock();
    }



    private void initToolbar() {
        RelativeLayout btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> handleBackPressed());

        mToolbarNext = findViewById(R.id.tv_next);
        mToolbarTitle = findViewById(R.id.tv_title);
    }

    private void initPreferences() {
        mPrefs = GlobalApplication
                .getAppContext()
                .getSharedPreferences("scanner", MODE_PRIVATE);
    }

    private void initViews() {
        mGlView = findViewById(R.id.glview_scan);
        mMainPage = findViewById(R.id.rl_main_page);
        mScannerPage = findViewById(R.id.ll_scanning_page);

        mOpenFromGalleryBtn = findViewById(R.id.btn_lower);
        mProgressBar = findViewById(R.id.pb_scanner);

        mDescription = findViewById(R.id.ll_description_container);
        mCounterText = findViewById(R.id.tv_counter);
        mUndoBtn = findViewById(R.id.btn_undo);

        mCropLayout = findViewById(R.id.rl_crop_container);
        mEffectLayout = findViewById(R.id.rl_effect_container);

        mEffectRV = findViewById(R.id.rv_effect);
//        mEffectRV.setHasFixedSize(true);

        mBtnSimple = findViewById(R.id.btn_complexity_simple);
        mBtnMedium = findViewById(R.id.btn_complexity_medium);
        mBtnDetailed = findViewById(R.id.btn_complexity_detailed);

        if (mBtnSimple != null) {
            mBtnSimple.setOnClickListener(v -> setComplexity(0));
            mBtnMedium.setOnClickListener(v -> setComplexity(1));
            mBtnDetailed.setOnClickListener(v -> setComplexity(2));
        }
    }

    private void setComplexity(int level) {
        mSelectedComplexity = level;

        TextView[] btns = {mBtnSimple, mBtnMedium, mBtnDetailed};
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] == null) continue;
            if (i == level) {
                btns[i].setBackgroundResource(R.drawable.bg_stats_mode_chip_active);
                btns[i].setTextColor(0xFF8B5CF6);
            } else {
                btns[i].setBackgroundResource(R.drawable.bg_stats_mode_chip);
                btns[i].setTextColor(0xFF9A90B0);
            }
        }

        if (mGlView != null) {
            mGlView.setScannerType(mSelectedComplexity);
        }
    }

    private void initEffectRecycler() {
        WrapContentLinearLayoutManager layoutManager =
                new WrapContentLinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);

        mEffectRV.setLayoutManager(layoutManager);
    }


    private void handleBackPressed() {
        if (this.mCurrentMode == 0) {
            finish();
        } else {
            gotoInitialMode();
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        mCountDownRunning = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkScanLock();
    }

    private void checkScanLock() {
        if (isScanningLocked()) {
            showLockedState();
        } else {
            showUnlockedState();
        }
    }

    private void showLockedState() {
        mCounterText.setVisibility(View.VISIBLE);

        mDescription.setOnClickListener(v -> showPurchaseDialog());

        if (!mCountDownRunning) {
            mCountDownRunning = true;
            startCountdown();
        }
    }

    private void showUnlockedState() {
        mCounterText.setVisibility(View.GONE);
        mCountDownRunning = false;
    }

    private void startCountdown() {
        mCountDownHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCountDownRunning) {
                    updateCountdown();
                    mCountDownHandler.postDelayed(this, 1000);
                }
            }
        });
    }

    private long getCurrentTime() {
        return System.currentTimeMillis();
    }


    private void updateCountdown() {
        long currentTime = getCurrentTime();

        // Calculate next lock reset time (same logic as before)
        Calendar nextReset = Calendar.getInstance();
        nextReset.setTimeInMillis(currentTime);
        normalizeToMidDay(nextReset);

        long millisRemaining = nextReset.getTimeInMillis() - currentTime;

        long hoursRemaining = (millisRemaining / 1000 / 60 / 60);
        long minutesRemaining = (currentTime / 1000 / 60) % 60;
        long secondsRemaining = (currentTime / 1000) % 60;

        // Update countdown text
        String text = MessageFormat.format(
                getString(R.string.book_scan_counter_format),
                hoursRemaining,
                minutesRemaining,
                secondsRemaining
        );
        mCounterText.setText(text);

        // When hour changes, re-check lock state
        if (mPrevHour != -1 && mPrevHour != hoursRemaining) {
            checkScanLock();
        }

        mPrevHour = hoursRemaining;
    }

    private boolean isScanningLocked() {
        boolean requiresSubscription = !PurchaseManager.getInstance().hasSubscription();

        // Allow first 3 scans regardless
        if (getScanCount() < 3) {
            return false;
        }

        // Normalize today's time to next 12-hour boundary (12:00)
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(getCurrentTime());
        normalizeToMidDay(today);

        // Normalize last scan time to next 12-hour boundary (12:00)
        Calendar lastScan = Calendar.getInstance();
        lastScan.setTimeInMillis(mPrefs.getLong(SCAN_TIME_KEY, 0));
        normalizeToMidDay(lastScan);

        // If same day → locked (if user not subscribed)
        boolean sameDay =
                today.get(Calendar.YEAR) == lastScan.get(Calendar.YEAR) &&
                        today.get(Calendar.DAY_OF_YEAR) == lastScan.get(Calendar.DAY_OF_YEAR);

        if (sameDay) {
            return requiresSubscription;
        }

        // Else → new day, reset scan count
        resetScanCount();
        return false;
    }

    private void normalizeToMidDay(Calendar calendar) {
        calendar.add(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }


    public int getScanCount() {
        return mPrefs.getInt(SCAN_COUNT_KEY, 0);
    }

    private void increaseScanCount() {
        int newCount = getScanCount() + 1;

        mPrefs.edit()
                .putInt(SCAN_COUNT_KEY, newCount)
                .putLong(SCAN_TIME_KEY, getCurrentTime())
                .apply();
    }

    private void resetScanCount() {
        mPrefs.edit()
                .putInt(SCAN_COUNT_KEY, 0)
                .putLong(SCAN_TIME_KEY, getCurrentTime())
                .apply();
    }

    public void showPurchaseDialog() {
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult requestCode:" + requestCode +
                " resultCode:" + resultCode + " data:" + data);

        switch (requestCode) {
            case RC_GALLERY:
                onRequestGalleryResult(false, resultCode, data);
                break;

            case RC_CAMERA:
                onRequestGalleryResult(true, resultCode, data);
                break;

            default:
                // Unknown request -> ignore safely
                break;
        }
    }


    private void onRequestGalleryResult(boolean fromCamera, int resultCode, Intent data) {

        if (resultCode != RESULT_OK) return;

        Uri uri = null;

        // 1. Try to get URI from Intent data
        if (data != null) {
            uri = data.getData();
            Log.d(TAG, "uri=" + uri);

            // fallback: try EXTRA_STREAM
            if (uri == null && data.getExtras() != null) {
                Uri streamUri = data.getParcelableExtra(Intent.EXTRA_STREAM);
                uri = streamUri;
                Log.d(TAG, "EXTRA_STREAM uri=" + streamUri);
            }
        }

        // 2. Fallback to static camera file
        if (uri == null) {
            Log.d(TAG, "using static name");
            uri = Uri.fromFile(getCameraFile());
            Log.d(TAG, "hardcoded uri=" + uri);
        }

        // 3. Load with ExecutorService
        loadImageAsync(uri, fromCamera);
    }

    private void loadImageAsync(final Uri uri, final boolean fromCamera) {

        executor.execute(() -> {
            Bitmap bitmap = null;

            try {
                bitmap = download(uri);
            } catch (IOException ignored) {
            }

            final Bitmap resultBitmap = bitmap;

            // Switch to UI thread
            mainHandler.post(() -> {
                if (resultBitmap != null) {
                    startScanner(resultBitmap, fromCamera);
                } else {
                    showScanningFailedDlg();
                }
            });
        });
    }


    public void startScanner(Bitmap bitmap, boolean isFromGallery) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null, cannot start scanner");
            return;
        }

        gotoCropMode();

        mGlView.startScanning(bitmap, isFromGallery, new ScannerGLView.ScannerGLViewListener() {

            @Override
            public void onScanningFailed() {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showScanningFailedDlg();
                    }
                });
            }

            @Override
            public void onScanningSuccess(Bitmap overlay, Bitmap index) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        // Clean up bitmaps if activity is dying
                        if (overlay != null && !overlay.isRecycled()) overlay.recycle();
                        if (index != null && !index.isRecycled()) index.recycle();
                        return;
                    }

                    try {
                        ScanProcessActivity.libraryItem = saveScan(overlay, index);

                        if (overlay != null && !overlay.isRecycled()) overlay.recycle();
                        if (index != null && !index.isRecycled()) index.recycle();

                        Intent result = new Intent();
                        result.putExtra("ScanOk", true);
                        setResult(RESULT_OK, result);
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onScanningSuccess", e);
                        Toast.makeText(ScanProcessActivity.this,
                                "Failed to save scan", Toast.LENGTH_SHORT).show();
                    }
                });

            }

            @Override
            public void onScanningStateChanged(boolean busy, boolean enableDone) {
                if (mCurrentMode != 3) return;

                // update only if state changed
                if (mScanningBusy != busy || mEnableDone != enableDone) {
                    mScanningBusy = busy;
                    mEnableDone = enableDone;

                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            updateScanningState();
                        }
                    });
                }
            }

            @Override
            public void onUndoStateUpdated(boolean canUndo) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        setUndoButtonState(canUndo);
                    }
                });
            }

            @Override
            public void gotoNextStage() {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && mCurrentMode != 3) {
                        gotoProcessingMode();
                    }
                });
            }
        });
    }


    private void setUndoButtonState(boolean enabled) {
        mUndoBtn.setEnabled(enabled);
        mUndoBtn.setAlpha(enabled ? 1f : 0f);
    }

    private void updateScanningState() {

        // Show/hide progress bar
        mProgressBar.setVisibility(mScanningBusy ? View.VISIBLE : View.GONE);

        // Update "Next / Done" button
        boolean isEnabled = mEnableDone;
        mToolbarNext.setEnabled(isEnabled);

        int color = getColor(isEnabled
                ? R.color.white_toolbar_btn
                : R.color.white_toolbar_btn_disabled);

        mToolbarNext.setTextColor(color);
    }

    private void showScanningFailedDlg() {
        runOnUiThread(() -> {
            Toast.makeText(
                    this,
                    getString(R.string.book_scan_failed),
                    Toast.LENGTH_SHORT
            ).show();

            gotoInitialMode();
        });
    }

    private void gotoInitialMode() {
        mCurrentMode = 0;

        // Reset scanner state
        mGlView.resetScanner();

        // Toolbar setup
        mToolbarTitle.setText(R.string.book_scan_toolbar_title);
        mToolbarNext.setVisibility(View.GONE);

        // Page visibility
        mMainPage.setVisibility(View.VISIBLE);
        mScannerPage.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
        mCropLayout.setVisibility(View.GONE);
        mEffectLayout.setVisibility(View.GONE);

        mOpenFromGalleryBtn.setOnClickListener(v -> {
            Log.d(TAG, "gallery");
            Intent intent = new Intent("android.intent.action.PICK", MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            Log.d(TAG, "intent " + intent);
            try {
                startActivityForResult(intent, RC_GALLERY);
            } catch (ActivityNotFoundException unused) {
                Log.d(TAG, "No activity found that can handle intent " + intent.getAction());
            }

        });
    }

    /**
     * Opens the gallery image picker safely.
     */

    private void gotoCropMode() {
        mCurrentMode = 2; // consider enum later

        // --- Toolbar Setup ---
        mToolbarTitle.setText(R.string.book_scan_toolbar_title_crop);

        mToolbarNext.setEnabled(true);
        mToolbarNext.setVisibility(View.VISIBLE);
        mToolbarNext.setText(R.string.book_scan_next);

        // Replace deprecated getColor(int) with ContextCompat
        int whiteColor = ContextCompat.getColor(this, R.color.white_toolbar_btn);
        mToolbarNext.setTextColor(whiteColor);

        mToolbarNext.setOnClickListener(v -> mGlView.acceptCrop());

        // --- Undo button disabled initially ---
        setUndoButtonState(false);

        // --- UI Visibility Changes ---
        mMainPage.setVisibility(View.GONE);
        mScannerPage.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
        mCropLayout.setVisibility(View.VISIBLE);
        mEffectLayout.setVisibility(View.GONE);
    }

    private void gotoProcessingMode() {
        mCurrentMode = 3;              // Consider using enum later
        mEnableDone = false;
        mScanningBusy = false;

        // Toolbar setup
        mToolbarTitle.setText(R.string.book_scan_toolbar_title_type);
        mToolbarNext.setVisibility(View.VISIBLE);
        mToolbarNext.setText(R.string.book_scan_done);

        mToolbarNext.setOnClickListener(v -> {
            v.setEnabled(false);
            mProgressBar.setVisibility(View.VISIBLE);
            mGlView.onScanningDone();
        });

        // UI transitions
        mMainPage.setVisibility(View.GONE);
        mScannerPage.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
        mCropLayout.setVisibility(View.GONE);
        mEffectLayout.setVisibility(View.VISIBLE);

        // Effect list
        ArrayList<EffectSelector.EffectItem> effects = new ArrayList<>();
        effects.add(new EffectSelector.EffectItem("Original", R.drawable.book_scan_import_original, null));
        effects.add(new EffectSelector.EffectItem("Light", R.drawable.book_scan_import_light, null));
        effects.add(new EffectSelector.EffectItem("Lighter", R.drawable.book_scan_import_lighter, null));
        effects.add(new EffectSelector.EffectItem("Dark", R.drawable.book_scan_import_dark, null));
        effects.add(new EffectSelector.EffectItem("Despecle", R.drawable.book_scan_import_despeckle, null));

        // Adapter setup
        mEffectAdapter = new EffectSelector.EffectAdapter(
                effects,
                R.layout.frag_sheet_effect_item,
                mOnEffectClickListener
        );

        mEffectRV.setAdapter(mEffectAdapter);
        mEffectAdapter.selectItem(0);

        // Default scanner selection
        mGlView.setScannerType(0);

        // Refresh processing state
        updateScanningState();
    }


    private LibraryItem saveScan(Bitmap bitmap, Bitmap bitmap2) {
        Log.d(TAG, "saveScan");
        if (bitmap == null || bitmap.isRecycled() || bitmap2 == null || bitmap2.isRecycled()) {
            Log.e(TAG, "saveScan: invalid bitmaps");
            return null;
        }
        Library.getInstance().mLibraryItemsChanged = true;
        String str = "scan" + System.currentTimeMillis();
        try {
            File file = new File(GlobalApplication.getAppContext().getFilesDir(), "scan");
            file.mkdirs();
            Log.d(TAG, "save " + str);
            File file2 = new File(file, str + ".jpg");
            File file3 = new File(file, str + "idx.png");
            Log.d(TAG, "save fileOverlay " + file2);
            Log.d(TAG, "save fileIndex " + file3);
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream);
            fileOutputStream.close();
            FileOutputStream fileOutputStream2 = new FileOutputStream(file3);
            bitmap2.compress(Bitmap.CompressFormat.PNG, 0, fileOutputStream2);
            fileOutputStream2.close();
            LibraryItem addScannedFileToTagCache = Library.getInstance().addScannedFileToTagCache(file2, null, true);
            increaseScanCount();
            Log.d(TAG, "save " + str);
            return addScannedFileToTagCache;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "save failed " + str);
            return null;
        }
    }


    private File getCameraFile() {
        return new File(GlobalApplication.getShareDirectory(), "camera.jpg");
    }

    private Bitmap download(Uri uri) throws IOException {
        int exifToDegrees;
        Log.d(TAG, "download " + uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        try {
            InputStream openInputStream = openInputStream(uri);
            Log.d(TAG, "input " + openInputStream);
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(openInputStream, null, options);
            openInputStream.close();
            InputStream openInputStream2 = openInputStream(uri);
            int i = options.outWidth;
            int i2 = options.outHeight;
            float max = Math.max(((float) options.outWidth) / 2048.0f, ((float) options.outHeight) / 2048.0f);
            Log.d(TAG, "image " + i + "x" + i2 + " scale:" + max);
            options.inJustDecodeBounds = false;
            options.inSampleSize = (int) Math.ceil((double) max);
            options.inPurgeable = false;
            Bitmap decodeStream = BitmapFactory.decodeStream(openInputStream2, null, options);
            Log.d(TAG, "bitmap " + decodeStream);
            openInputStream2.close();
            String contentFilename = getContentFilename(uri);
            File cameraFile = getCameraFile();
            if (contentFilename != null && !contentFilename.equals(cameraFile.getAbsolutePath())) {
                InputStream openInputStream3 = openInputStream(uri);
                AssetHelper.copyInputStreamToFile(openInputStream3, cameraFile);
                openInputStream3.close();
                contentFilename = cameraFile.getAbsolutePath();
            }
            if (!(contentFilename == null || (exifToDegrees = ExifHelper.exifToDegrees(new ExifInterface(contentFilename).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1))) == 0)) {
                Log.d(TAG, "Need to rotate, exif rotation " + exifToDegrees);
                decodeStream = ExifHelper.convertBitmapToCorrectOrientation(decodeStream, exifToDegrees);
            }
            if (decodeStream == null) {
                return decodeStream;
            }
            Log.d(TAG, "bitmap " + decodeStream.getWidth() + "x" + decodeStream.getHeight() + " " + decodeStream.getConfig());
            return decodeStream;
        } catch (RuntimeException e) {
            Log.d(TAG, "Failed to open image\n" + e.toString());
            getCameraFile().delete();
            return null;
        }
    }



    private InputStream openInputStream(@NonNull Uri uri) throws FileNotFoundException {
        Log.d(TAG, "openInputStream: " + uri);

        String scheme = uri.getScheme();

        if ("file".equalsIgnoreCase(scheme)) {
            // Direct file access
            String path = uri.getPath();
            if (path == null) {
                throw new FileNotFoundException("File URI path is null");
            }
            try {
                return Files.newInputStream(Paths.get(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // SAF / MediaStore / Cloud providers
        InputStream stream = getContentResolver().openInputStream(uri);

        if (stream == null) {
            throw new FileNotFoundException("Unable to open InputStream for URI: " + uri);
        }

        return stream;
    }

    public String getContentFilename(Uri uri) {
        if (uri == null) return null;

        // If it's a file:// URI → return actual path
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }

        // For content:// URIs → read display name
        Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    return cursor.getString(nameIndex);
                }
            } finally {
                cursor.close();
            }
        }

        // Fallback if display name is missing
        return uri.getLastPathSegment();
    }

    public void onClickExample2(View view) {
        onClickExample("scan_example2.jpg");
    }

    public void onClickExample3(View view) {
        onClickExample("scan_example3.jpg");
    }

    private void onClickExample(String assetName) {
        startScanner(AssetHelper.loadBitmap("assets/" + assetName), false);
    }


    public void onClickScannerUndo(View view) {
        if (view != null) {
            view.animate()
                    .scaleX(0.9f).scaleY(0.9f)
                    .setDuration(80)
                    .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(80));
        }

        if (mGlView != null) {
            mGlView.undo();
        }
    }

    public void onClickScannerEraser(View view) {
        if (view == null || mGlView == null) return;

        boolean selected = !view.isSelected();
        view.setSelected(selected);

        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(80));

        mGlView.toggleEraser(selected);
    }

}
