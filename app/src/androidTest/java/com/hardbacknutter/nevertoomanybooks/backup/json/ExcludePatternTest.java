/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.SharedPreferences;
import android.util.Log;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.SharedPreferencesCoder;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcludePatternTest
        extends BaseDBTest {

    private static final String TAG = "ExcludePatternTest";

    private SharedPreferences preferences;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        preferences = ServiceLocator.getInstance().getSharedPreferences();
        preferences.edit()
                   .putString("acra.blah", "1")
                   .putString("blah.acra", "1")
                   .putString("acrablah", "1")
                   .putString(CoverVolume.PK_VOLUME_INDEX, "1")
                   .putString("Some.Other.Key", "1")
                   .apply();
    }

    @Test
    void encoding() {
        final JsonCoder<SharedPreferences> encoder = SharedPreferencesCoder.createEncoder(
                Excludes.EXCLUDE_WHEN_EXPORTING);

        final JSONObject jsonObject = encoder.encode(preferences);

        assertTrue(jsonObject.has(AppLocale.PK_UI_LOCALE));

        assertFalse(jsonObject.has("acra.blah"));
        assertTrue(jsonObject.has("blah.acra"));
        assertTrue(jsonObject.has("acrablah"));
        assertFalse(jsonObject.has(CoverVolume.PK_VOLUME_INDEX));
        assertTrue(jsonObject.has("Some.Other.Key"));

        Log.d(TAG, jsonObject.toString(2));
    }
}
