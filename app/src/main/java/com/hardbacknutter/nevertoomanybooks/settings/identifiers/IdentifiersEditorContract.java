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

package com.hardbacknutter.nevertoomanybooks.settings.identifiers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivityLauncher;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsOutput;

public class IdentifiersEditorContract
        extends ActivityResultContract<Void, Optional<SettingsOutput>> {

    /**
     * Whether to show the fragment/views that allows the user to edit external id's (sid).
     * This used to be for books only, but is now generic usage.
     * <p>
     * {@code boolean}
     */
    public static final String PK_EDIT_BOOK_TABS_EXTERNAL_ID = "edit.book.tab.externalId";

    /**
     * Check if the {@code external id} edit tab should be shown.
     * This is an 'advanced' user preference.
     *
     * @return flag
     */
    public static boolean isShowExternalIdTab() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_EDIT_BOOK_TABS_EXTERNAL_ID, false);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               final Void unused) {
        return FragmentHostActivityLauncher
                .createIntent(context, IdentifiersAdminFragment.class,
                              R.layout.activity_main_tabbar);
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
