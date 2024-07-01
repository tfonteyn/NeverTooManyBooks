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

package com.hardbacknutter.nevertoomanybooks.booklist.filters.ui;

import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;

public class PFilterViewHolder
        extends RecyclerView.ViewHolder {

    @NonNull
    protected final ModificationListener listener;

    /**
     * Constructor.
     *
     * @param itemView the view specific for this holder
     * @param listener for update events
     */
    PFilterViewHolder(@NonNull final View itemView,
                      @NonNull final ModificationListener listener) {
        super(itemView);

        this.listener = listener;
        final Button delBtn = itemView.findViewById(R.id.btn_del);
        if (delBtn != null) {
            delBtn.setOnClickListener(v -> listener.onDelete(getBindingAdapterPosition()));
        }
    }
}
