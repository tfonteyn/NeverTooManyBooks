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

package com.hardbacknutter.nevertoomanybooks.entities.merging;

import android.content.Context;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

public class TocEntryPruner
        implements Pruner<TocEntry> {

    private final EntityMerger<TocEntry> mergeHelper;
    private final boolean normalise;
    @Nullable
    private IdFixer<TocEntry> idFixer;
    private ReorderHelper reorderHelper;

    /**
     * Constructor.
     * <p>
     * Uses the preferred setting for normalisation.
     */
    public TocEntryPruner() {
        this(ReorderHelper.isTryReorderingDuringDeduplication());
    }

    /**
     * Constructor.
     *
     * @param normalise flag, whether to normalise the name
     */
    public TocEntryPruner(final boolean normalise) {
        this.normalise = normalise;
        this.idFixer = ServiceLocator.getInstance().getTocEntryDao();
        mergeHelper = new EntityMerger<>();
    }

    /**
     * Test constructor. The {@link #idFixer} is set to {@code null}.
     *
     * @param normalise flag, whether to normalise the name
     *
     * @return new instance
     */
    @VisibleForTesting
    @NonNull
    public static TocEntryPruner forTest(final boolean normalise) {
        final TocEntryPruner pruner = new TocEntryPruner(normalise);
        pruner.idFixer = null;
        return pruner;
    }

    @Override
    public boolean prune(@NonNull final Context context,
                         @NonNull final Collection<TocEntry> list,
                         @NonNull final Function<TocEntry, Locale> localeSupplier) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        if (normalise) {
            normalise(context, list, localeSupplier);
        }

        return mergeHelper.merge(context, list, localeSupplier, idFixer);
    }

    private void normalise(@NonNull final Context context,
                           @NonNull final Collection<TocEntry> list,
                           @NonNull final Function<TocEntry, Locale> localeSupplier) {
        if (reorderHelper == null) {
            final LocaleList userLocales = context.getResources().getConfiguration()
                                                  .getLocales();
            final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
            reorderHelper = new ReorderHelper(allLocales);
        }
        list.forEach(tocEntry -> {
            final String title = reorderHelper.reverse(context, tocEntry.getTitle(),
                                                       localeSupplier.apply(tocEntry));
            tocEntry.setTitle(title);
        });
    }
}
