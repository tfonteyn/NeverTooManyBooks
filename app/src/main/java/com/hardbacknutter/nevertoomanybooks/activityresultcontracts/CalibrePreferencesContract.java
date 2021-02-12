/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostActivity;

public class CalibrePreferencesContract
        extends ActivityResultContract<Void, Void> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @Nullable final Void aVoid) {
        return new Intent(context, SettingsHostActivity.class)
                .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, CalibrePreferencesFragment.TAG);
    }

    @Override
    @Nullable
    public Void parseResult(final int resultCode,
                            @Nullable final Intent intent) {
        return null;
    }
}
