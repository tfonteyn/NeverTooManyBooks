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

package com.hardbacknutter.nevertoomanybooks.settings.tags;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivityLauncher;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsOutput;

public class TagAdminContract
        extends ActivityResultContract<Void, Optional<SettingsOutput>> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               final Void unused) {
        return FragmentHostActivityLauncher
                .createIntent(context, TagAdminFragment.class, R.layout.activity_main_tabbar);
    }

    @Override
    public Optional<SettingsOutput> parseResult(final int resultCode,
                                                @Nullable final Intent intent) {

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        return SettingsOutput.fromBundle(intent.getExtras());
    }
}
