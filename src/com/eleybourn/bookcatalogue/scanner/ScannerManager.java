package com.eleybourn.bookcatalogue.scanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to handle details of specific scanner interfaces and return a
 * Scanner object to the caller.
 *
 * @author pjw
 */
public class ScannerManager {
    private static final String TAG = "ScannerManager.";

    /** Preference key */
    public static final String PREF_PREFERRED_SCANNER = TAG + "PreferredScanner";

    /** Unique IDs to associate with each supported scanner intent */
    public static final int SCANNER_ZXING_COMPATIBLE = 1;
    public static final int SCANNER_PIC2SHOP = 2;
    public static final int SCANNER_ZXING = 3;

    /** Collection of ScannerFactory objects */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, ScannerFactory> myScannerFactories = new HashMap<>();

    /*
     * Build the collection
     */
    static {
        // free and easy
        myScannerFactories.put(SCANNER_ZXING, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new ZxingScanner(true);
            }

            @Override
            public boolean isIntentAvailable(final @NonNull Context context) {
                return ZxingScanner.isIntentAvailable(context,true);
            }
        });

        // not free, but better in bad conditions
        myScannerFactories.put(SCANNER_PIC2SHOP, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new Pic2ShopScanner();
            }

            @Override
            public boolean isIntentAvailable(final @NonNull Context context) {
                return Pic2ShopScanner.isIntentAvailable(context);
            }
        });

        // bit of a fallback.
        myScannerFactories.put(SCANNER_ZXING_COMPATIBLE, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new ZxingScanner(false);
            }

            @Override
            public boolean isIntentAvailable(final @NonNull Context context) {
                return ZxingScanner.isIntentAvailable(context,false);
            }
        });
    }

    /**
     * Return a Scanner object based on the current environment and user preferences.
     *
     * @return A Scanner, or null when none found
     */
    @Nullable
    public static Scanner getScanner(final @NonNull Activity activity) {
        // Find out what the user prefers if any
        int prefScanner = BookCatalogueApp.getIntPreference(PREF_PREFERRED_SCANNER, SCANNER_ZXING_COMPATIBLE);

        // See if preferred one is present, if so return a new instance
        ScannerFactory psf = myScannerFactories.get(prefScanner);
        if (psf != null && psf.isIntentAvailable(activity)) {
            return psf.newInstance();
        }

        // Search all supported scanners; return first working one
        for (ScannerFactory sf : myScannerFactories.values()) {
            if (sf != psf && sf.isIntentAvailable(activity)) {
                return sf.newInstance();
            }
        }
        return null;
    }

    /**
     * We don't have a scanner setup or it was bad. Prompt the user for installing one.
     */
    public static void promptForScannerInstallAndFinish(final @NonNull Activity activity, final boolean noScanner) {
        int messageId = (noScanner ? R.string.info_install_scanner : R.string.warning_bad_scanner);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.title_install_scan)
                .setMessage(messageId)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        // we use POS and NEG for scanner choice. If we ever support 3, redo this lazy hack.
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                /* text hardcoded as a it is a product name */
                "ZXing",
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        installScanner(activity,"market://details?id=com.google.zxing.client.android");
                    }
                });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                /* text hardcoded as a it is a product name */
                "pic2shop",
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        installScanner(activity,"market://details?id=com.visionsmarts.pic2shop");
                    }
                });

        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        //do nothing
                        activity.setResult(Activity.RESULT_CANCELED);
                        activity.finish();
                    }
                });

        dialog.show();
    }

    private static void installScanner(final @NonNull Activity activity, final @NonNull String uri) {
        try {
            Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(uri));
            activity.startActivity(marketIntent);
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        } catch (ActivityNotFoundException e) {
            // Google Play (market) not installed
            StandardDialogs.showUserMessage(activity, R.string.error_google_play_missing);
            Logger.error(e);
        }
    }

    /**
     * Support for creating scanner objects on the fly without knowing which ones are available.
     *
     * @author pjw
     */
    private interface ScannerFactory {
        /** Create a new scanner of the related type */
        @NonNull
        Scanner newInstance();

        /** Check if this scanner is available */
        boolean isIntentAvailable(final @NonNull Context context);
    }

}
