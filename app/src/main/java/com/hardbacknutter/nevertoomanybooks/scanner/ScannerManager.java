/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Class to handle details of specific scanner interfaces and return a
 * Scanner object to the caller.
 * <p>
 * Pro and cons for the Scanners.
 * <ul>
 *      <li>embedded: easiest for the user; developer must support/upgrade it</li>
 *      <li>Google Play Services: near automatic install.<br>
 *          But won't work on devices without Google.<br>
 *          Decoding is done AFTER the picture was taken so might force the user to repeat.</li>
 *      <li>ZXing scanner: user must install manually.<br>
 *          App is no longer updated (since early 2019)on the Google store,
 *          and the ZXing github site docs state the project in is maintenance mode.</li>
 *      <li>Pic2Shop: allegedly better than ZXing. Comments on the app not that good however.</li>
 * </ul>
 * <p>
 * NEWTHINGS: adding a scanner
 * - implement the {@link Scanner} interface
 * - create a {@link ScannerFactory} for it and add to {@link #SCANNER_FACTORIES}
 * - add a new identifier here below
 * - add string/number to res/arrays/pe_scanning_preferred_scanner
 * - (optional) add to {@link #ALL_ACTIONS}.
 */
public final class ScannerManager {

    /**
     * All actions for the supported scanner variants.
     * <strong>Only used by the debug report</strong> to report on installed scanners.
     * See {@link #collectDebugInfo}.
     */
    private static final String[] ALL_ACTIONS = new String[]{
            MediaStore.ACTION_IMAGE_CAPTURE,
            com.google.zxing.client.android.Intents.Scan.ACTION,
            Pic2ShopScanner.Free.ACTION,
            Pic2ShopScanner.Pro.ACTION,
            };

    /** Unique ID's to associate with each supported scanner intent. */
    private static final int EMBEDDED_ZXING = 0;
    private static final int GOOGLE_PLAY_SERVICES = 1;
    private static final int ZXING = 2;
    private static final int ZXING_COMPATIBLE = 3;
    private static final int PIC2SHOP = 4;

    private static final int DEFAULT = EMBEDDED_ZXING;

    /** Collection of ScannerFactory objects. */
    private static final SparseArray<Class<? extends ScannerFactory>>
            SCANNER_FACTORIES = new SparseArray<>();

    static {
        SCANNER_FACTORIES.put(EMBEDDED_ZXING,
                              EmbeddedZxingScanner.EmbeddedZxingScannerFactory.class);
        SCANNER_FACTORIES.put(GOOGLE_PLAY_SERVICES,
                              GoogleBarcodeScanner.GoogleBarcodeScannerFactory.class);
        SCANNER_FACTORIES.put(ZXING, ZxingScanner.ZxingScannerFactory.class);
        SCANNER_FACTORIES.put(ZXING_COMPATIBLE, ZxingScanner.ZxingCompatibleScannerFactory.class);
        SCANNER_FACTORIES.put(PIC2SHOP, Pic2ShopScanner.Pic2ShopScannerFactory.class);
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
        try {
            // Preferred scanner available?
            final ScannerFactory psf =
                    SCANNER_FACTORIES.get(getPreferredScanner(context)).newInstance();
            if (psf.isAvailable(context)) {
                return psf.getScanner(context);
            }

            // Search all supported scanners; return first working one
            for (int i = 0; i < SCANNER_FACTORIES.size(); i++) {
                final ScannerFactory sf = SCANNER_FACTORIES.valueAt(i).newInstance();
                if (sf != psf && sf.isAvailable(context)) {
                    return sf.getScanner(context);
                }
            }
        } catch (@NonNull final IllegalAccessException | InstantiationException ignore) {
            // ignore
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
        ScannerFactory sf = null;
        try {
            sf = SCANNER_FACTORIES.get(getPreferredScanner(activity)).newInstance();
        } catch (@NonNull final IllegalAccessException | InstantiationException ignore) {
            // ignore
        }
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
                final String marketUrl = sf.getMarketUrl();
                if (marketUrl != null && !marketUrl.isEmpty()) {
                    final Uri uri = Uri.parse(marketUrl);
                    final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    try {
                        activity.startActivity(intent);
                        // returning true is not a guarantee for the install working...
                        resultListener.onResult(true);
                        return;
                    } catch (@NonNull final ActivityNotFoundException ignore) {
                        // ignore
                    }
                    googlePlayStoreMissing(activity, resultListener);
                }
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

        final GoogleApiAvailability gApi = GoogleApiAvailability.getInstance();
        final int status = gApi.isGooglePlayServicesAvailable(activity);

        if (status == ConnectionResult.SUCCESS) {
            // up-to-date, ready to use.
            resultListener.onResult(true);

        } else {
            final Dialog dialog = gApi.getErrorDialog(activity, status,
                                                      RequestCode.UPDATE_GOOGLE_PLAY_SERVICES,
                                                      d -> {
                                                          setPreferredScanner(activity,
                                                                              ZXING_COMPATIBLE);
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

        final String msg = context.getString(R.string.error_google_play_store_missing) + '\n'
                           + context.getString(R.string.txt_install_scanner_recommendation);

        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setOnCancelListener(d -> resultListener.onResult(false))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.cancel())
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

    private static int getPreferredScanner(@NonNull final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Prefs.getListPreference(prefs, Prefs.pk_scanner_preferred, DEFAULT);
    }

    static boolean isBeepOnBarcodeFound(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_sounds_scan_found_barcode, true);
    }

    public static boolean isBeepOnValid(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_sounds_scan_isbn_valid, false);
    }

    public static boolean isBeepOnInvalid(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_sounds_scan_isbn_invalid, true);
    }

    /**
     * Collect information on scanners for the debug report.
     *
     * @param context Current context
     *
     * @return report
     */
    public static String collectDebugInfo(@NonNull final Context context) {

        final StringBuilder message = new StringBuilder();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Scanners installed
        try {
            message.append("Preferred Scanner: ")
                   .append(Prefs.getListPreference(prefs, Prefs.pk_scanner_preferred, -1))
                   .append('\n');

            for (String scannerAction : ALL_ACTIONS) {
                message.append("Scanner [").append(scannerAction).append("]:\n");
                final Intent scannerIntent = new Intent(scannerAction, null);
                final List<ResolveInfo> resolved =
                        context.getPackageManager().queryIntentActivities(scannerIntent, 0);

                if (!resolved.isEmpty()) {
                    for (ResolveInfo r : resolved) {
                        message.append("    ");
                        // Could be activity or service...
                        if (r.activityInfo != null) {
                            message.append(r.activityInfo.packageName);
                        } else if (r.serviceInfo != null) {
                            message.append(r.serviceInfo.packageName);
                        } else {
                            message.append("UNKNOWN");
                        }
                        message.append(" (priority ").append(r.priority)
                               .append(", preference ").append(r.preferredOrder)
                               .append(", match ").append(r.match)
                               .append(", default=").append(r.isDefault)
                               .append(")\n");
                    }
                } else {
                    message.append("    No packages found\n");
                }
            }
        } catch (@NonNull final RuntimeException e) {
            message.append("Scanner info failure: ").append(e.getLocalizedMessage()).append('\n');
        }

        return message.toString();
    }

    public interface OnResultListener {

        void onResult(boolean success);
    }
}
