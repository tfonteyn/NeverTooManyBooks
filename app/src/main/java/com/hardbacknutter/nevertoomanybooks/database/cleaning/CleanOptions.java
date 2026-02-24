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

package com.hardbacknutter.nevertoomanybooks.database.cleaning;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;

public enum CleanOptions {
    RemoveDuplicateAuthors,
    RemoveDuplicateSeries,
    RemoveDuplicatePublishers,
    /**
     * When set, RemoveDuplicateAuthors <strong>MUST</strong> also be set.
     * This is NOT automatic on purpose.
     */
    RemoveDuplicateTocEntries,
    /**
     * Purge orphans from all applicable tables.
     */
    Purge,
    /**
     * Check for, and fix, authors which directly or indirectly reference themselves
     * as a pseudonym.
     */
    ResolveAuthors;

    private static final String PK_OPTIONS = StartupViewModel.PK_RUN_MAINTENANCE + ".options";

    /**
     * Set cleaner options to use when the cleaner is started.
     * Typically, after setting options, the cleaner should be scheduled by calling BOTH:
     * <pre>
     *   // Run the cleaner to remove duplicates as configured above
     *   StartupViewModel.schedule(context,
     *       StartupViewModel.PK_RUN_MAINTENANCE, true);
     *
     *   // and rebuild both OB columns and the indexes
     *   StartupViewModel.schedule(context,
     *       StartupViewModel.PK_REBUILD_INDEXES, true);
     * </pre>
     *
     * @param options to set
     */
    public static void setOptions(@NonNull final Set<CleanOptions> options) {

        final Set<String> all = options.stream()
                                       .map(Enum::name)
                                       .collect(Collectors.toSet());

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putStringSet(PK_OPTIONS, all)
                      .apply();
    }

    @SuppressWarnings({"CheckStyle", "OverlyBroadCatchBlock"})
    @NonNull
    static Set<CleanOptions> readOptions() {
        @Nullable
        final Set<String> all = ServiceLocator.getInstance().getSharedPreferences()
                                              .getStringSet(PK_OPTIONS, null);

        final Set<CleanOptions> options = EnumSet.noneOf(CleanOptions.class);
        if (all != null) {
            for (final String option : all) {
                try {
                    options.add(valueOf(option));
                } catch (@NonNull final Exception ignored) {
                    // skip invalid/missing enum values
                }
            }
        }

        return options;
    }

    static void clearOptions() {
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .remove(PK_OPTIONS)
                      .apply();
    }
}
