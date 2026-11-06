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

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public class IdentifierValuePruner {

    /**
     * Remove duplicates. We keep the first occurrence.
     * <p>
     * Dev note: the {@code sid} value is actually handled here, but in a pure-data way.
     * i.o.w. not as a linked piece of key.
     *
     * @param list List to clean up
     *
     * @return {@code true} if the list was modified.
     */
    public boolean prune(@NonNull final Collection<Identifier.Value> list) {
        if (list.isEmpty()) {
            return false;
        }

        final Set<String> keysFound = new HashSet<>(list.size());
        final Iterator<Identifier.Value> iterator = list.iterator();
        boolean modified = false;

        while (iterator.hasNext()) {
            final Identifier.Value iv = iterator.next();
            if (!isValidIdentifier(iv) || !keysFound.add(iv.getKey().toLowerCase(Locale.ENGLISH))) {
                iterator.remove();
                modified = true;
            }
        }

        return modified;
    }

    private boolean isValidIdentifier(@NonNull final Identifier.Value iv) {
        final String sid = iv.getSid();
        // Not just a sanity check: we MUST check for null!
        //noinspection ConstantValue
        return sid != null && !sid.isEmpty() && !"0".equals(sid);
    }
}
