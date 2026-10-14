package com.colourmate.paintbynumber.model.game;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.DataManager;


public class SavePreviewTask extends AsyncTask<Object, Void, Void> {
    public void savePreview(Level level, Bitmap bitmap) {
        execute(level, bitmap);
    }

    
    @Override
    public Void doInBackground(Object... objArr) {
        DataManager.getInstance().savePreview((Level) objArr[0], (Bitmap) objArr[1]);
        return null;
    }
}
