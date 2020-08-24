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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.scanner.GoogleBarcodeScanner;

/**
 * Used/defined in xml/preferences.xml
 */
public class BarcodePreferenceFragment
        extends BasePreferenceFragment {

    private static final String TAG = "BarcodePreferenceFrag";

    /**
     * The user modified the scanner in preferences (or not).
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_SCANNER_MODIFIED = TAG + ":scannerModified";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        setPreferencesFromResource(R.xml.preferences_barcodes, rootKey);

        // Start this as fire-and-forget runnable
        // perhaps delay this until the user selects the google scanner?
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new GoogleBarcodeScanner.PreloadGoogleScanner());
    }
}
