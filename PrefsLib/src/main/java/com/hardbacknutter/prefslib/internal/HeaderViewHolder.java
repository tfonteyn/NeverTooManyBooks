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

package com.hardbacknutter.prefslib.internal;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.Function;

import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.databinding.PrefsLibHeaderBinding;

public final class HeaderViewHolder
        extends SettingViewHolder {

    private HeaderViewHolder(@NonNull final PrefsLibHeaderBinding vb,
                             @NonNull final Function<Integer, Setting> itemProvider,
                             @NonNull final UICallback listener) {
        super(vb.getRoot(), vb.icon, vb.title, vb.summary);
        itemView.setOnClickListener(v -> {
            final int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onClick(itemProvider.apply(position));
            }
        });
    }

    @NonNull
    static HeaderViewHolder create(@NonNull final LayoutInflater inflater,
                                   @NonNull final ViewGroup parent,
                                   @NonNull final Function<Integer, Setting> itemProvider,
                                   @NonNull final UICallback listener) {
        final PrefsLibHeaderBinding vb = PrefsLibHeaderBinding
                .inflate(inflater, parent, false);
        return new HeaderViewHolder(vb, itemProvider, listener);
    }
}
