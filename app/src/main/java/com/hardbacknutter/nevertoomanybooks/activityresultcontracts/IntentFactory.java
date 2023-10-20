/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.material.appbar.AppBarLayout;

import com.hardbacknutter.nevertoomanybooks.AboutFragment;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Some {@link Intent} creation methods which are used more than once.
 */
public final class IntentFactory {

    private IntentFactory() {
    }

    @NonNull
    public static Intent createHelpIntent(@NonNull final Context context) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.help_url)));
    }

    @NonNull
    public static Intent createAboutIntent(@NonNull final Context context) {
        final Intent intent = FragmentHostActivity.createIntent(context, AboutFragment.class);
        intent.putExtra(FragmentHostActivity.BKEY_TOOLBAR_SCROLL_FLAGS,
                        AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL);
        return intent;
    }
}
