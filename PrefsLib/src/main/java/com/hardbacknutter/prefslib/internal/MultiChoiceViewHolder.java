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

import com.hardbacknutter.prefslib.MultiChoiceSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.databinding.PrefsLibTextViewBinding;

public final class MultiChoiceViewHolder
        extends SettingViewHolder {

    private MultiChoiceViewHolder(@NonNull final PrefsLibTextViewBinding vb,
                                  @NonNull final Function<Integer, Setting> itemProvider,
                                  @NonNull final UICallback listener) {
        super(vb.getRoot(), vb.icon, vb.title, vb.summary);

        itemView.setOnClickListener(v -> {
            final int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            final MultiChoiceSetting setting = (MultiChoiceSetting) itemProvider.apply(position);
            listener.showDialog(setting);
        });
    }

    @NonNull
    static MultiChoiceViewHolder create(@NonNull final LayoutInflater inflater,
                                        @NonNull final ViewGroup parent,
                                        @NonNull final Function<Integer, Setting> itemProvider,
                                        @NonNull final UICallback listener) {
        final PrefsLibTextViewBinding vb = PrefsLibTextViewBinding
                .inflate(inflater, parent, false);
        return new MultiChoiceViewHolder(vb, itemProvider, listener);
    }
}
