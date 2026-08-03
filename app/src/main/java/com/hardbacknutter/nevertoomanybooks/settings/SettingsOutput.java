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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ContractOutput;

public final class SettingsOutput
        implements ContractOutput {

    private static final String TAG = "SettingsOutput";
    /** Something changed (or not) that requires a recreation of the caller Activity. */
    private static final String BKEY_RECREATE_ACTIVITY = TAG + ":recreate";
    /** Something changed (or not) that requires a rebuild of the Booklist. */
    private static final String BKEY_REBUILD_BOOKLIST = TAG + ":rebuildList";

    private final boolean recreateActivity;
    private final boolean forceRebuildBooklist;

    public SettingsOutput(final boolean recreateActivity,
                          final boolean forceRebuildBooklist) {
        this.recreateActivity = recreateActivity;
        this.forceRebuildBooklist = forceRebuildBooklist;
    }

    @NonNull
    public static Optional<SettingsOutput> fromBundle(@Nullable final Bundle args) {
        if (args == null) {
            return Optional.empty();
        }

        final boolean recreateActivity = args.getBoolean(BKEY_RECREATE_ACTIVITY, false);
        final boolean forceRebuildBooklist = args.getBoolean(BKEY_REBUILD_BOOKLIST, false);

        return Optional.of(new SettingsOutput(recreateActivity, forceRebuildBooklist));
    }

    @Override
    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putBoolean(BKEY_RECREATE_ACTIVITY, recreateActivity);
        args.putBoolean(BKEY_REBUILD_BOOKLIST, forceRebuildBooklist);
        return args;
    }

    public boolean isRecreateActivity() {
        return recreateActivity;
    }

    public boolean isForceRebuildBooklist() {
        return forceRebuildBooklist;
    }

    @Override
    @NonNull
    public String toString() {
        return "SettingsOutput{"
               + "recreateActivity=" + recreateActivity
               + ", forceRebuildBooklist=" + forceRebuildBooklist
               + '}';
    }
}
