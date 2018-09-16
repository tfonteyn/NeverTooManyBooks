package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * This object will start a Zxing compatible scanner and extract the data
 * from the resulting intent when the activity completes.
 *
 * It also has a static method to check if the intent is present.
 *
 * @author pjw
 */
public class ZxingScanner implements Scanner {
    public static final String ACTION = "com.google.zxing.client.android.SCAN";
    private static final String PACKAGE = "com.google.zxing.client.android";

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
     * @return true if present
     */
    public static boolean isIntentAvailable(final boolean mustBeZxing) {
        return isIntentAvailable(BookCatalogueApp.getAppContext(), mustBeZxing ? PACKAGE : null);
    }

    /**
     * Check if the passed intent action is available.
     *
     * @return true if present
     */
    private static boolean isIntentAvailable(@NonNull final Context ctx, @Nullable final String packageName) {
        Intent test = new Intent(ACTION);
        if (packageName != null && !packageName.isEmpty()) {
            test.setPackage(packageName);
        }
        return ctx.getPackageManager().resolveActivity(test, 0) != null;
    }

    /**
     * Start the activity with the passed request code.
     */
    @Override
    public void startActivityForResult(@NonNull final Activity a, final int requestCode) {
        Intent i = new Intent(ACTION);
        if (mMustBeZxing) {
            i.setPackage(PACKAGE);
        }
        a.startActivityForResult(i, requestCode);
    }

    /**
     * Extract the barcode from the result
     */
    @Override
    public String getBarcode(@NonNull final Intent intent) {
        return intent.getStringExtra(Scanner.SCAN_RESULT);
    }

}
