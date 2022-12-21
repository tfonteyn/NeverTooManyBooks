/*
 * @Copyright 2018-2022 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;

public class PublisherMergeHelper
        extends EntityMergeHelper<Publisher> {

    @Override
    protected boolean merge(@NonNull final Publisher previous,
                            @NonNull final Publisher current) {
        // if the previous one has no id, and the current does, then we copy the id.
        if (previous.getId() == 0 && current.getId() > 0) {
            previous.setId(current.getId());
        }
        // no other attributes, so we can always merge
        return true;
    }
}
