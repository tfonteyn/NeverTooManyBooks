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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Class to handle details of specific scanner interfaces and return a
 * Scanner object to the caller.
 */
public final class ScannerManager {

    /** Unique ID's to associate with each supported scanner intent. */
    public static final int ZXING_COMPATIBLE = 0;
    public static final int ZXING = 1;
    public static final int PIC2SHOP = 2;

    /** All actions for the supported scanner variants. */
    public static final String[] ALL_ACTIONS = new String[]{
            ZxingScanner.ACTION,
            Pic2ShopScanner.Free.ACTION,
            Pic2ShopScanner.Pro.ACTION,
            };

    /** Collection of ScannerFactory objects. */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, ScannerFactory> SCANNER_FACTORIES = new HashMap<>();

    /*
     * Build the collection.
     */
    static {
        // free and easy
        SCANNER_FACTORIES.put(ZXING, new ZxingScanner.ZxingScannerFactory());
        // not free, but allegedly better in bad conditions
        SCANNER_FACTORIES.put(PIC2SHOP, new Pic2ShopScanner.Pic2ShopScannerFactory());
        // bit of a fallback.
        SCANNER_FACTORIES.put(ZXING_COMPATIBLE, new ZxingScanner.ZxingCompatibleScannerFactory());
    }

    private ScannerManager() {
    }

    /**
     * We don't have a scanner setup or it was bad. Prompt the user for installing one.
     */
    public static void promptForScannerInstallAndFinish(@NonNull final Activity activity,
                                                        final boolean noScanner) {
        int messageId = noScanner ? R.string.info_install_scanner
                                  : R.string.warning_bad_scanner;

        Menu menu = MenuPicker.createMenu(activity);
        menu.add(Menu.NONE, R.id.MENU_SCANNER_ZXING, 0, ZxingScanner.DISPLAY_NAME)
            .setIcon(R.drawable.ic_scanner);
        menu.add(Menu.NONE, R.id.MENU_SCANNER_PIC2SHOP, 0, Pic2ShopScanner.DISPLAY_NAME)
            .setIcon(R.drawable.ic_scanner);

        new MenuPicker<Void>(activity, R.string.title_install_scan, messageId, true,
                             menu, null, (menuItem, userObject) -> {
            switch (menuItem.getItemId()) {
                case R.id.MENU_SCANNER_ZXING:
                    installScanner(activity, ZxingScanner.MARKET_URL);
                    return true;

                case R.id.MENU_SCANNER_PIC2SHOP:
                    installScanner(activity, Pic2ShopScanner.MARKET_URL);
                    return true;

                default:
                    return false;
            }
        }).show();
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
     * Return a Scanner object based on the current environment and user preferences.
     *
     * @return A Scanner, or {@code null} if none found
     */
    @Nullable
    public static Scanner getScanner(@NonNull final Context context) {
        int prefScanner = App.getListPreference(Prefs.pk_scanner_preferred, ZXING_COMPATIBLE);

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
     * Support for creating scanner objects on the fly without knowing which ones are available.
     */
    public interface ScannerFactory {

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
