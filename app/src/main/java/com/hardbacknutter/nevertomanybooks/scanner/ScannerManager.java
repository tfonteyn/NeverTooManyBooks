/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.scanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.utils.UserMessage;

/**
 * Class to handle details of specific scanner interfaces and return a
 * Scanner object to the caller.
 */
public final class ScannerManager {

    /** Unique ID's to associate with each supported scanner intent. */
    public static final int SCANNER_ZXING_COMPATIBLE = 0;
    public static final int SCANNER_ZXING = 1;
    public static final int SCANNER_PIC2SHOP = 2;
    private static final String MARKET_URL_ZXING =
            "market://details?id=com.google.zxing.client.android";
    private static final String MARKET_URL_PIC2SHOP =
            "market://details?id=com.visionsmarts.pic2shop";
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
     * @return A Scanner, or {@code null} if none found
     */
    @Nullable
    public static Scanner getScanner(@NonNull final Context context) {
        // Find out what the user prefers if any
        int prefScanner = App.getListPreference(Prefs.pk_scanning_preferred_scanner,
                                                SCANNER_ZXING_COMPATIBLE);

        // See if preferred one is present, if so return a new instance
        ScannerFactory psf = SCANNER_FACTORIES.get(prefScanner);
        if (psf != null && psf.isIntentAvailable(context)) {
            return psf.newInstance();
        }

        // Search all supported scanners; return first working one
        for (ScannerFactory sf : SCANNER_FACTORIES.values()) {
            if (sf != psf && sf.isIntentAvailable(context)) {
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

        new AlertDialog.Builder(activity)
                .setIcon(R.drawable.ic_scanner)
                .setTitle(R.string.title_install_scan)
                .setMessage(messageId)
                .setNeutralButton(android.R.string.cancel, (d, w) -> {
                    //do nothing
                    activity.setResult(Activity.RESULT_CANCELED);
                    activity.finish();
                })
                // we use POS and NEG for scanner choice. If we ever support 3, redo this lazy hack.
                // text hardcoded as a it is a product name
                .setNegativeButton("pic2shop",
                                   (d, which) -> installScanner(activity, MARKET_URL_PIC2SHOP))
                .setPositiveButton("ZXing",
                                   (d, which) -> installScanner(activity, MARKET_URL_ZXING))
                .create()
                .show();
    }

    private static void installScanner(@NonNull final Activity activity,
                                       @NonNull final String uri) {
        try {
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            activity.startActivity(marketIntent);
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        } catch (@NonNull final ActivityNotFoundException e) {
            UserMessage.show(activity, R.string.error_google_play_missing);
            Logger.warn(ScannerManager.class, "installScanner", e);
        }
    }

    /**
     * Support for creating scanner objects on the fly without knowing which ones are available.
     */
    private interface ScannerFactory {

        /**
         * @return a new scanner of the related type.
         */
        @NonNull
        Scanner newInstance();

        /**
         * @return {@code true} if this scanner is available.
         */
        boolean isIntentAvailable(@NonNull Context context);
    }

}
