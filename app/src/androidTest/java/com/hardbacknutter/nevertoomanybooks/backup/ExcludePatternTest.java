/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.SharedPreferencesCoder;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExcludePatternTest
        extends BaseDBTest {

    private static final String TAG = "ExcludePatternTest";

    private SharedPreferences preferences;

    @Before
    public void setup()
            throws DaoWriteException, StorageException, IOException, DataReaderException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit()
                   .putString("acra.blah", "1")
                   .putString("blah.acra", "1")
                   .putString("acrablah", "1")
                   .putString(CoverVolume.PK_VOLUME_INDEX, "1")
                   .putString("Some.Other.Key", "1")
                   .apply();
    }

    @Test
    public void encoding() {
        final JsonCoder<SharedPreferences> encoder = SharedPreferencesCoder.createEncoder(
                Prefs.EXCLUDE_WHEN_EXPORTING);

        final JSONObject jsonObject = encoder.encode(preferences);

        assertTrue(jsonObject.has(Prefs.PK_UI_LOCALE));

        assertFalse(jsonObject.has("acra.blah"));
        assertTrue(jsonObject.has("blah.acra"));
        assertTrue(jsonObject.has("acrablah"));
        assertFalse(jsonObject.has(CoverVolume.PK_VOLUME_INDEX));
        assertTrue(jsonObject.has("Some.Other.Key"));


        Log.d(TAG, jsonObject.toString(2));

    }
}
