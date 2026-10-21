package com.colourmate.paintbynumber.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.colourmate.paintbynumber.GlobalApplication;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Manages user credits and purchases using EncryptedSharedPreferences.
 * This class follows a singleton pattern to provide a global access point to token/credit data.
 */
public class TokenManager {

    private static final String PREFS_NAME = "secure_token_prefs";
    private static final String CREDITS_LEFT_KEY = "creditsLeft";
    private static final int DEFAULT_CREDITS = 4000;

    public static final String TAG = "TokenManager";

    private static final TokenManager instance = new TokenManager();
    private SharedPreferences sharedPreferences;

    /**
     * Private constructor to prevent instantiation.
     * Initializes EncryptedSharedPreferences for secure storage.
     */
    private TokenManager() {
        try {
            Context context = GlobalApplication.getAppContext();
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            this.sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error initializing EncryptedSharedPreferences: " + e.getMessage());
            // Fallback to regular SharedPreferences ONLY IF encryption fails (optional, based on security policy)
            this.sharedPreferences = GlobalApplication.getAppContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * Returns the singleton instance of the TokenManager.
     */
    public static TokenManager getInstance() {
        return instance;
    }

    /**
     * Gets the current number of credits the user has.
     *
     * @return The number of credits.
     */
    public int getCredits() {
        return this.sharedPreferences.getInt(CREDITS_LEFT_KEY, DEFAULT_CREDITS);
    }

    /**
     * Adds a specified amount of credits to the user's balance.
     *
     * @param amountToAdd The number of credits to add.
     */
    public void addCredits(int amountToAdd) {
        int newCredits = getCredits() + amountToAdd;
        sharedPreferences.edit().putInt(CREDITS_LEFT_KEY, newCredits).apply();
    }

    /**
     * Attempts to purchase an item using credits. If the user has enough credits,
     * the cost is deducted and the item is marked as purchased.
     *
     * @param itemId The unique identifier for the item being purchased.
     * @param cost   The cost of the item in credits.
     * @return true if the purchase was successful, false otherwise.
     */
    public boolean buy(@NonNull String itemId, int cost) {
        int currentCredits = getCredits();
        if (cost > currentCredits) {
            return false; // Not enough credits
        }

        int newCredits = currentCredits - cost;
        sharedPreferences.edit()
                .putInt(CREDITS_LEFT_KEY, newCredits)
                .putBoolean(itemId, true)
                .apply();

        return true;
    }

    /**
     * Deducts credits for using a hint.
     *
     * @param hintCost The cost of the hint.
     * @return true if the user had enough credits, false otherwise.
     */
    public boolean useHint(int hintCost) {
        int currentCredits = getCredits();
        if (hintCost > currentCredits) {
            return false; // Not enough credits
        }
        int newCredits = currentCredits - hintCost;
        sharedPreferences.edit().putInt(CREDITS_LEFT_KEY, newCredits).apply();
        return true;
    }

    /**
     * Checks if a specific item has been purchased.
     *
     * @param itemId The unique identifier for the item.
     * @return true if the item has been purchased, false otherwise.
     */
    public boolean isPurchased(@NonNull String itemId) {
        return this.sharedPreferences.getBoolean(itemId, false);
    }

    /**
     * Marks an item as purchased without deducting credits.
     *
     * @param itemId The unique identifier for the item to unlock.
     * @return always true, as the underlying save operation is asynchronous.
     */
    public boolean unlock(@NonNull String itemId) {
        sharedPreferences.edit().putBoolean(itemId, true).apply();
        return true;
    }
}
