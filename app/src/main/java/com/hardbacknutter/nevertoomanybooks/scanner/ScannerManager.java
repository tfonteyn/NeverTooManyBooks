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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.Menu;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * FIXME: this is needlessly complicated now that we support Google Play Barcode scanning.
 * Given that the ZXing scanner app is no longer updated (since early 2019)on the Google store,
 * and the ZXing github site docs state the project in is maintenance mode,
 * it's probably best to simply remove all other scanners.
 *
 * Class to handle details of specific scanner interfaces and return a
 * Scanner object to the caller.
 */
public final class ScannerManager {

    /** Unique ID's to associate with each supported scanner intent. */
    public static final int GOOGLE_PLAY_SERVICES = 0;
    public static final int ZXING_COMPATIBLE = 1;
    public static final int ZXING = 2;
    public static final int PIC2SHOP = 3;

    /** All actions for the supported scanner variants. Only used by the debug report. */
    public static final String[] ALL_ACTIONS = new String[]{
            GoogleBarcodeScanner.ACTION,
            ZxingScanner.ACTION,
            Pic2ShopScanner.Free.ACTION,
            Pic2ShopScanner.Pro.ACTION,
            };

    /** Collection of ScannerFactory objects. */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, ScannerFactory> SCANNER_FACTORIES = new HashMap<>();

    /*
     * Build the collection. Same order as the constants.
     */
    static {
        // universal
        SCANNER_FACTORIES
                .put(GOOGLE_PLAY_SERVICES, new GoogleBarcodeScanner.GooglePlayScannerFactory());
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
     * We don't have a scanner setup or it was bad.
     * Prompt the user for installing one we know of; but the user is not limited to these as long
     * as other scanners support a compatible intent.
     *
     * @param activity  calling Activity
     * @param noScanner Set {@code true} if there simply was no scanner
     *                  Set {@code false} if the scanner was bad
     */
    public static void promptForScanner(@NonNull final Activity activity,
                                        final boolean noScanner) {

        GoogleApiAvailability gApi = GoogleApiAvailability.getInstance();
        boolean hasGooglePS = gApi.isGooglePlayServicesAvailable(activity)
                              != ConnectionResult.SERVICE_INVALID;
        if (!hasGooglePS) {
            String msg = activity.getString(R.string.error_google_play_missing) + '\n'
                         + activity.getString(R.string.info_install_scanner_manually,
                                              activity.getString(
                                                      R.string.pv_scanner_zxing_compatible));
            new AlertDialog.Builder(activity)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.pg_barcode_scanner)
                    .setMessage(msg)
                    .setNegativeButton(android.R.string.cancel,
                                       (d, which) -> {
                                           activity.setResult(Activity.RESULT_CANCELED);
                                           activity.finish();
                                       })
                    .create()
                    .show();
            return;
        }

        int messageId = noScanner ? R.string.info_install_scanner
                                  : R.string.warning_bad_scanner;

        Menu menu = MenuPicker.createMenu(activity);
        // do not add the Google Play Services if they are not available on this device.
        for (ScannerFactory sf : SCANNER_FACTORIES.values()) {
            menu.add(Menu.NONE, sf.getResId(), 0, sf.getLabel(activity))
                .setIcon(R.drawable.ic_scanner);
        }

        new MenuPicker<Void>(activity, R.string.title_install_scan, messageId, true,
                             menu, null, (menuItem, userObject) -> {

            if (menuItem.getItemId() == R.id.MENU_SCANNER_GOOGLE_PLAY) {
                GoogleBarcodeScanner.GooglePSInstall installResult =
                        GoogleBarcodeScanner.installGooglePS(
                                activity, d -> {
                                    ScannerManager.setNextScannerAsDefault(activity);
                                    activity.setResult(Activity.RESULT_CANCELED);
                                    activity.finish();
                                });

                switch (installResult) {
                    case Success:
                        activity.setResult(Activity.RESULT_OK);
                        activity.finish();
                        return true;

                    case PromptedUser:
                        // just wait for the dialog to be done.
                        // Either via the cancelListener above; or activity#onActivityResult
                        return true;

                    case NotAvailable:
                        // should not happen, log but ignore.
                        Logger.warn(ScannerManager.class, "promptForScanner",
                                    "Google Play Services not available on this device");
                        activity.setResult(Activity.RESULT_CANCELED);
                        activity.finish();
                        return true;
                }
            } else {
                for (ScannerFactory sf : SCANNER_FACTORIES.values()) {
                    if (menuItem.getItemId() == sf.getResId()) {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                                   Uri.parse(sf.getMarketUrl()));
                        try {
                            activity.startActivity(intent);
                        } catch (@NonNull final ActivityNotFoundException e) {
                            // should not happen, log but ignore.
                            Logger.warn(ScannerManager.class, "promptForScanner",
                                        "Google Play Services not available on this device",
                                        e);
                        }

                        activity.setResult(Activity.RESULT_CANCELED);
                        activity.finish();
                        return true;
                    }
                }
            }
            return true;

        }).show();
    }

    private static void setPreferredScanner(@NonNull final Context context,
                                            final int scanner) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(Prefs.pk_scanner_preferred, String.valueOf(scanner))
                         .apply();
    }

    private static void setNextScannerAsDefault(@NonNull final Context context) {
        int scanner = getPreferredScanner();
        setPreferredScanner(context, scanner + 1);
    }

    private static int getPreferredScanner() {
        return App.getListPreference(Prefs.pk_scanner_preferred, GOOGLE_PLAY_SERVICES);
    }

    /**
     * Support for creating scanner objects on the fly without knowing which ones are available.
     */
    public interface ScannerFactory {

        /**
         * Displayed to the user.
         *
         * @param context Current context
         *
         * @return name
         */
        @NonNull
        String getLabel(@NonNull Context context);

        /**
         * Get a resource id that can be used in menus.
         *
         * @return resource id
         */
        @IdRes
        int getResId();

        /**
         * Get the market url, or the empty string if not applicable.
         * The caller must check on {@code }isEmpty()}.
         *
         * @return the market url, or "".
         */
        @NonNull
        String getMarketUrl();

        /**
         * @param context Current context
         *
         * @return a new scanner of the related type.
         */
        @NonNull
        Scanner newInstance(@NonNull Context context);

        /**
         * @param context Current context
         *
         * @return {@code true} if this scanner is available.
         */
        boolean isAvailable(@NonNull Context context);

    }
}
