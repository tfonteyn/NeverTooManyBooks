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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;

public class TagPruner
        implements Pruner<Tag> {

    private final EntityMerger<Tag> mergeHelper;
    @Nullable
    private IdFixer<Tag> idFixer;

    /**
     * Constructor.
     */
    public TagPruner() {
        idFixer = (context, item, locale) ->
                ServiceLocator.getInstance().getTagDao().fixId(item);
        mergeHelper = new EntityMerger<>();
    }

    /**
     * Test constructor. The {@link #idFixer} is set to {@code null}.
     *
     * @return new instance
     */
    @VisibleForTesting
    @NonNull
    public static TagPruner forTest() {
        final TagPruner pruner = new TagPruner();
        pruner.idFixer = null;
        return pruner;
    }

    @Override
    public boolean prune(@NonNull final Context context,
                         @NonNull final Collection<Tag> list,
                         @NonNull final Function<Tag, Locale> localeSupplier) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        return mergeHelper.merge(context, list, localeSupplier, idFixer);
    }
}
