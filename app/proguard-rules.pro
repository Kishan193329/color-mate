# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.facebook.infer.annotation.Nullsafe$Mode
-dontwarn com.facebook.infer.annotation.Nullsafe

# Keep the entire Editor class and its methods called from JNI
-keep class com.sumoing.recolor.editor.Editor {
    public static void wakeupCallback();
    # Add any other JNI-called methods here
}

# Or keep all native-related methods in the class
-keepclassmembers class com.sumoing.recolor.editor.Editor {
    native <methods>;
    public static *;
}

# Keep the entire class with all members
-keep class com.sumoing.recolor.editor.Editor { *; }
-keep class com.colourmate.paintbynumber.ads.AdsManager$AdModel { *; }
-keep class com.colourmate.paintbynumber.ads.MediationManager$MediationModel { *; }

# Keep members of the AdModel for Firebase/GSON mapping
-keepclassmembers class com.colourmate.paintbynumber.ads.AdsManager$AdModel {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.colourmate.paintbynumber.ads.MediationManager$MediationModel {
    <fields>;
    <init>(...);
}


# ==============================================================================
# General Rules
# ==============================================================================
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Also keep all classes in the ads package that might be used as POJOs
-keepclassmembers class com.colourmate.paintbynumber.ads.** {
    public <fields>;
    public <methods>;
}

# Firebase Realtime Database
-keep class com.google.firebase.database.** { *; }

# ==============================================================================
# Google Play Billing (IAP)
# ==============================================================================
-keep class com.android.billingclient.api.** { *; }
-keep class com.android.vending.billing.** { *; }
-keep interface com.android.billingclient.api.** { *; }

# ==============================================================================
# Google AdMob (Mobile Ads)
# ==============================================================================
-keep public class com.google.android.gms.ads.** {
   public *;
}

-keep public class com.google.ads.mediation.** {
   public *;
}

# For AdMob ad events and listeners
-keep class com.google.android.gms.ads.AdListener { *; }
-keep class com.google.android.gms.ads.FullScreenContentCallback { *; }
-keep class com.google.android.gms.ads.AdError { *; }
-keep class com.google.android.gms.ads.LoadAdError { *; }

# (Facebook Audience Network rules removed 2026-10-14 — the SDK is no longer a dependency.)

# ==============================================================================
# Mediaton / AdX / Other
# ==============================================================================
-keep class com.colourmate.paintbynumber.ads.** { *; }

# ==============================================================================
# WorkManager (daily reminder, 2026-10-14)
# WorkManager stores the worker's CLASS NAME in its database and re-creates it by reflection,
# so an obfuscated name that changes between builds would break every scheduled reminder
# after an app update. Keep the name and the (Context, WorkerParameters) constructor.
# ==============================================================================
-keep class com.colourmate.paintbynumber.util.DailyReminder$ReminderWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Handle Javascript interfaces if used (usually needed for some ad networks)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}