/*
 * @Copyright 2018-2024 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Some {@link Intent} creation methods which are used more than once.
 */
public final class IntentFactory {

    private IntentFactory() {
    }

    @NonNull
    public static Intent createGithubHelpIntent(@NonNull final Context context) {
        return new Intent(Intent.ACTION_VIEW,
                          Uri.parse(context.getString(R.string.github_help_url)));
    }

    @NonNull
    public static Intent createGithubIssueIntent(@NonNull final Context context) {
        return new Intent(Intent.ACTION_VIEW,
                          Uri.parse(context.getString(R.string.github_issues_url)));
    }
}
