package com.eleybourn.bookcatalogue.scanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to handle details of specific scanner interfaces and return a
 * Scanner object to the caller.
 *
 * @author pjw
 */
public final class ScannerManager {

    /** Unique IDs to associate with each supported scanner intent. */
    private static final int SCANNER_ZXING_COMPATIBLE = 0;
    private static final int SCANNER_PIC2SHOP = 1;
    private static final int SCANNER_ZXING = 2;

    /** Collection of ScannerFactory objects. */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, ScannerFactory> SCANNER_FACTORIES = new HashMap<>();

    /*
     * Build the collection.
     */
    static {
        // free and easy
        SCANNER_FACTORIES.put(SCANNER_ZXING, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new ZxingScanner(true);
            }

            @Override
            public boolean isIntentAvailable(@NonNull final Context context) {
                return ZxingScanner.isIntentAvailable(context, true);
            }
        });

        // not free, but better in bad conditions
        SCANNER_FACTORIES.put(SCANNER_PIC2SHOP, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new Pic2ShopScanner();
            }

            @Override
            public boolean isIntentAvailable(@NonNull final Context context) {
                return Pic2ShopScanner.isIntentAvailable(context);
            }
        });

        // bit of a fallback.
        SCANNER_FACTORIES.put(SCANNER_ZXING_COMPATIBLE, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new ZxingScanner(false);
            }

            @Override
            public boolean isIntentAvailable(@NonNull final Context context) {
                return ZxingScanner.isIntentAvailable(context, false);
            }
        });
    }

    private ScannerManager() {
    }

    /**
     * Return a Scanner object based on the current environment and user preferences.
     *
     * @return A Scanner, or null when none found
     */
    @Nullable
    public static Scanner getScanner(@NonNull final Activity activity) {
        // Find out what the user prefers if any
        int prefScanner = Prefs.getInt(R.string.pk_scanning_preferred_scanner,
                                       SCANNER_ZXING_COMPATIBLE);

        // See if preferred one is present, if so return a new instance
        ScannerFactory psf = SCANNER_FACTORIES.get(prefScanner);
        if (psf != null && psf.isIntentAvailable(activity)) {
            return psf.newInstance();
        }

        // Search all supported scanners; return first working one
        for (ScannerFactory sf : SCANNER_FACTORIES.values()) {
            if (sf != psf && sf.isIntentAvailable(activity)) {
                return sf.newInstance();
            }
        }
        return null;
    }

    /**
     * We don't have a scanner setup or it was bad. Prompt the user for installing one.
     */
    public static void promptForScannerInstallAndFinish(@NonNull final Activity activity,
                                                        final boolean noScanner) {
        int messageId = noScanner ? R.string.info_install_scanner
                                   : R.string.warning_bad_scanner;

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.title_install_scan)
                .setMessage(messageId)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        // we use POS and NEG for scanner choice. If we ever support 3, redo this lazy hack.
        dialog.setButton(
                AlertDialog.BUTTON_POSITIVE,
                /* text hardcoded as a it is a product name */
                "ZXing",
                new DialogInterface.OnClickListener() {
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        installScanner(activity,
                                       "market://details?id=com.google.zxing.client.android");
                    }
                });
        dialog.setButton(
                AlertDialog.BUTTON_NEGATIVE,
                /* text hardcoded as a it is a product name */
                "pic2shop",
                new DialogInterface.OnClickListener() {
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        installScanner(activity,
                                       "market://details?id=com.visionsmarts.pic2shop");
                    }
                });

        dialog.setButton(
                AlertDialog.BUTTON_NEUTRAL, activity.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        //do nothing
                        activity.setResult(Activity.RESULT_CANCELED);
                        activity.finish();
                    }
                });

        dialog.show();
    }

    private static void installScanner(@NonNull final Activity activity,
                                       @NonNull final String uri) {
        try {
            Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                                             Uri.parse(uri));
            activity.startActivity(marketIntent);
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        } catch (ActivityNotFoundException e) {
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

        /** Create a new scanner of the related type. */
        @NonNull
        Scanner newInstance();

        /** Check if this scanner is available. */
        boolean isIntentAvailable(@NonNull final Context context);
    }

}
