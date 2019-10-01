/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.scanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Class to handle details of specific scanner interfaces and return a
 * Scanner object to the caller.
 * <p>
 * Pro and cons for the Scanners.
 * <ul>
 * <li>Google Play Services: near automatic install.<br>
 * But won't work on devices without Google.<br>
 * Decoding is done AFTER the picture was taken so might force the user to repeat.</li>
 * <li>ZXing scanner app: user must install manually.<br>
 * App is no longer updated (since early 2019)on the Google store,
 * and the ZXing github site docs state the project in is maintenance mode.
 * </li>
 * <li>Pic2Shop: allegedly better then ZXing. Comments on the app not that good however.</li>
 * </ul>
 * <p>
 * NEWTHINGS: adding a scanner
 * - implement the {@link Scanner} interface
 * - create a {@link ScannerFactory} for it and add to {@link #SCANNER_FACTORIES}
 * - add to {@link #ALL_ACTIONS} and add a new identifier here below.
 */
public final class ScannerManager {

    /** All actions for the supported scanner variants. Only used by the debug report. */
    public static final String[] ALL_ACTIONS = new String[]{
            MediaStore.ACTION_IMAGE_CAPTURE,
            ZxingScanner.ACTION,
            Pic2ShopScanner.Free.ACTION,
            Pic2ShopScanner.Pro.ACTION,
            };

    /** Unique ID's to associate with each supported scanner intent. */
    public static final int GOOGLE_PLAY_SERVICES = 0;
    public static final int DEFAULT = GOOGLE_PLAY_SERVICES;
    public static final int ZXING_COMPATIBLE = 1;
    public static final int ZXING = 2;
    public static final int PIC2SHOP = 3;

    /** Collection of ScannerFactory objects. */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, ScannerFactory> SCANNER_FACTORIES = new HashMap<>();

    /*
     * Build the collection. Same order as the constants.
     */
    static {
        // universal
        SCANNER_FACTORIES
                .put(GOOGLE_PLAY_SERVICES, new GoogleBarcodeScanner.GoogleBarcodeScannerFactory());
        // universal
        SCANNER_FACTORIES.put(ZXING_COMPATIBLE, new ZxingScanner.ZxingCompatibleScannerFactory());
        // the original ZXing (or ZXing+)
        SCANNER_FACTORIES.put(ZXING, new ZxingScanner.ZxingScannerFactory());
        // Pic2Shop is not free, but allegedly better in bad conditions
        SCANNER_FACTORIES.put(PIC2SHOP, new Pic2ShopScanner.Pic2ShopScannerFactory());
    }

    private ScannerManager() {
    }

    /**
     * Return a Scanner object based on the current environment and user preferences.
     *
     * @param context Current context
     *
     * @return A Scanner, or {@code null} if none found
     */
    @Nullable
    public static Scanner getScanner(@NonNull final Context context) {

        // See if preferred one is present, if so return a new instance
        ScannerFactory psf = SCANNER_FACTORIES.get(getPreferredScanner());
        if (psf != null && psf.isAvailable(context)) {
            return psf.newInstance(context);
        }
        // Search all supported scanners; return first working one
        for (ScannerFactory sf : SCANNER_FACTORIES.values()) {
            if (sf != psf && sf.isAvailable(context)) {
                return sf.newInstance(context);
            }
        }
        // up to the caller to tell the user.
        return null;
    }

    /**
     * Checks and/or prompts to install the preferred Scanner.
     *
     * @param activity       Calling activity
     * @param resultListener will be called with the outcome
     */
    public static void installScanner(@NonNull final Activity activity,
                                      @NonNull final OnResultListener resultListener) {
        ScannerFactory sf = SCANNER_FACTORIES.get(getPreferredScanner());
        //noinspection ConstantConditions
        if (!sf.isAvailable(activity)) {
            installScanner(activity, sf, resultListener);
        } else {
            resultListener.onResult(true);
        }
    }

    /**
     * Check/prompt to install the passed Scanner(Factory).
     *
     * @param activity       Calling activity
     * @param sf             ScannerFactory for the desired scanner
     * @param resultListener will be called with the outcome
     */
    private static void installScanner(@NonNull final Activity activity,
                                       @NonNull final ScannerFactory sf,
                                       @NonNull final OnResultListener resultListener) {

        if (sf.getMenuId() == R.id.MENU_SCANNER_GOOGLE_PLAY) {
            updateGooglePlayServices(activity, resultListener);

        } else {
            // without the store, we can't do any automatic installs.
            if (isGooglePlayStoreInstalled(activity)) {
                Uri uri = Uri.parse(sf.getMarketUrl());
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                try {
                    activity.startActivity(intent);
                    // returning true is not a guarantee for the install working...
                    resultListener.onResult(true);
                    return;
                } catch (@NonNull final ActivityNotFoundException ignore) {
                }
                googlePlayStoreMissing(activity, resultListener);
            }
        }
    }

    /**
     * Check if the device has Google Play Services.
     * <p>
     * If anything fails or the user cancels, we set the default scanner to ZXING_COMPATIBLE.
     *
     * @param activity       Calling activity
     * @param resultListener will be called with the outcome
     */
    private static void updateGooglePlayServices(@NonNull final Activity activity,
                                                 @NonNull final OnResultListener resultListener) {

        GoogleApiAvailability gApi = GoogleApiAvailability.getInstance();
        int status = gApi.isGooglePlayServicesAvailable(activity);

        if (status == ConnectionResult.SUCCESS) {
            // up-to-date, ready to use.
            resultListener.onResult(true);

        } else {
            Dialog dialog = gApi.getErrorDialog(activity, status,
                                                UniqueId.REQ_UPDATE_GOOGLE_PLAY_SERVICES,
                                                d -> {
                                                    setPreferredScanner(activity, ZXING_COMPATIBLE);
                                                    resultListener.onResult(false);
                                                });
            // Paranoia...
            if (dialog == null) {
                resultListener.onResult(true);
                return;
            }

            dialog.show();
        }
    }

    /**
     * Check if the device has Google Play Store.
     *
     * @param context Current context
     *
     * @return {@code true} if installed.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isGooglePlayStoreInstalled(@NonNull final Context context) {
        try {
            context.getPackageManager()
                   .getPackageInfo(GooglePlayServicesUtil.GOOGLE_PLAY_STORE_PACKAGE, 0);
            return true;
        } catch (@NonNull final PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Inform the user their device does not support the Google Play Store and what
     * to install manually.
     *
     * @param context        Current context
     * @param resultListener will be called with the outcome
     */
    private static void googlePlayStoreMissing(@NonNull final Context context,
                                               @NonNull final OnResultListener resultListener) {

        String msg = context.getString(R.string.error_google_play_store_missing) + '\n'
                     + context.getString(R.string.info_install_scanner_recommendation);

        new AlertDialog.Builder(context)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setOnCancelListener(d -> resultListener.onResult(false))
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.cancel())
                .create()
                .show();
    }

    public static void setDefaultScanner(@NonNull final Context context) {
        setPreferredScanner(context, DEFAULT);
    }

    private static void setPreferredScanner(@NonNull final Context context,
                                            final int scannerId) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(Prefs.pk_scanner_preferred, String.valueOf(scannerId))
                         .apply();
    }

    private static int getPreferredScanner() {
        return App.getListPreference(Prefs.pk_scanner_preferred, DEFAULT);
    }

    public interface OnResultListener {

        void onResult(boolean success);
    }
}
