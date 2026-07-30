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

package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

public final class SettingsOutput {
    private static final String TAG = "SettingsOutput";

    /** Something changed (or not) that requires a recreation of the caller Activity. */
    private static final String BKEY_RECREATE_ACTIVITY = TAG + ":recreate";
    /** Something changed (or not) that requires a rebuild of the Booklist. */
    private static final String BKEY_REBUILD_BOOKLIST = TAG + ":rebuildList";
    private final boolean recreateActivity;
    private final boolean forceRebuildBooklist;

    public SettingsOutput(@NonNull final Bundle result) {
        recreateActivity = result.getBoolean(BKEY_RECREATE_ACTIVITY, false);
        forceRebuildBooklist = result.getBoolean(BKEY_REBUILD_BOOKLIST, false);
    }

    /**
     * Create the result which {@code #parseResult(int, Intent)} will receive.
     *
     * @param recreateActivity     flag indicating if the BoB <strong>Activity</strong>
     *                             should be recreated
     * @param forceRebuildBooklist flag indicating if the BoB <strong>Booklist</strong>
     *                             should be rebuilt
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(final boolean recreateActivity,
                                      final boolean forceRebuildBooklist) {
        return new Intent().putExtra(BKEY_RECREATE_ACTIVITY, recreateActivity)
                           .putExtra(BKEY_REBUILD_BOOKLIST, forceRebuildBooklist);
    }

    public boolean isRecreateActivity() {
        return recreateActivity;
    }

    public boolean isForceRebuildBooklist() {
        return forceRebuildBooklist;
    }
}
