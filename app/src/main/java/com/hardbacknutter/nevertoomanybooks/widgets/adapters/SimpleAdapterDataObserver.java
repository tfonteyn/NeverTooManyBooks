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
package com.hardbacknutter.nevertoomanybooks.widgets.adapters;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Really dumb implementation that simple delegates everything to {@link #onChanged()}.
 */
public abstract class SimpleAdapterDataObserver
        extends RecyclerView.AdapterDataObserver {

    @Override
    public void onItemRangeChanged(final int positionStart,
                                   final int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeInserted(final int positionStart,
                                    final int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeRemoved(final int positionStart,
                                   final int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeMoved(final int fromPosition,
                                 final int toPosition,
                                 final int itemCount) {
        onChanged();
    }
}
