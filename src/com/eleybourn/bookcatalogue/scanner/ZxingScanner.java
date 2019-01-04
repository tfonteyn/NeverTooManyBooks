package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * This object will start a Zxing compatible scanner and extract the data
 * from the resulting intent when the activity completes.
 *
 * https://github.com/zxing
 *
 * Supported formats:
 * https://github.com/zxing/zxing/blob/master/core/src/main/java/com/google/zxing/BarcodeFormat.java
 *
 * It also has a static method to check if the intent is present.
 *
 * @author pjw
 */
public class ZxingScanner implements Scanner {

    public static final String ACTION = "com.google.zxing.client.android.SCAN";
    private static final String PACKAGE = "com.google.zxing.client.android";

    private static final String SCAN_RESULT = "SCAN_RESULT";

    /** Set to true of the Zxing package is required */
    private final boolean mMustBeZxing;

    /**
     * Constructor
     *
     * @param mustBeZxingPackage Set to true if the Zxing scanner app MUST be used
     */
    ZxingScanner(final boolean mustBeZxingPackage) {
        mMustBeZxing = mustBeZxingPackage;
    }

    /**
     * Check if we have a valid intent available.
     *
     * @return <tt>true</tt> if present
     */
    static boolean isIntentAvailable(@NonNull final Context context,
                                     final boolean mustBeZxing) {
        return isIntentAvailable(context, mustBeZxing ? PACKAGE : null);
    }

    /**
     * Check if the passed intent action is available.
     *
     * @return <tt>true</tt> if present
     */
    private static boolean isIntentAvailable(@NonNull final Context context,
                                             @Nullable final String packageName) {
        Intent intent = new Intent(ACTION);
        if (packageName != null && !packageName.isEmpty()) {
            intent.setPackage(packageName);
        }
        return context.getPackageManager().resolveActivity(intent, 0) != null;
    }

    /**
     * Start the activity with the passed request code.
     */
    @Override
    public void startActivityForResult(@NonNull final Activity activity,
                                       final int requestCode) {
        Intent intent = new Intent(ACTION);
        if (mMustBeZxing) {
            intent.setPackage(PACKAGE);
        }
        // not limiting the format, just grab anything supported.
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Extract the barcode from the result
     */
    @NonNull
    @Override
    public String getBarcode(@NonNull final Intent data) {
        return data.getStringExtra(SCAN_RESULT);
    }

}
