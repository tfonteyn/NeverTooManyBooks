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
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.Function;

import com.hardbacknutter.prefslib.BooleanSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.databinding.PrefsLibSwitchBinding;

public final class SwitchViewHolder
        extends SettingViewHolder {

    @NonNull
    private final PrefsLibSwitchBinding vb;
    @NonNull
    private final CompoundButton.OnCheckedChangeListener changeListener;

    private SwitchViewHolder(@NonNull final PrefsLibSwitchBinding vb,
                             @NonNull final Function<Integer, Setting> itemProvider,
                             @NonNull final UICallback listener) {
        super(vb.getRoot(), vb.icon, vb.title, vb.summary);
        this.vb = vb;

        changeListener = (buttonView, newValue) -> {
            final int position = SwitchViewHolder.this.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            final BooleanSetting setting = (BooleanSetting) itemProvider.apply(position);
            listener.onChange(setting, newValue);
        };

        vb.widget.setOnCheckedChangeListener(changeListener);

        // A click on the row itself acts the same as directly on the switch
        vb.getRoot().setOnClickListener(rowView -> {
            final int position = this.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            final BooleanSetting setting = (BooleanSetting) itemProvider.apply(position);
            listener.onChange(setting, !setting.isChecked());
        });
    }

    @NonNull
    static SwitchViewHolder create(@NonNull final LayoutInflater inflater,
                                   @NonNull final ViewGroup parent,
                                   @NonNull final Function<Integer, Setting> itemProvider,
                                   @NonNull final UICallback listener) {
        final PrefsLibSwitchBinding vb = PrefsLibSwitchBinding
                .inflate(inflater, parent, false);
        return new SwitchViewHolder(vb, itemProvider, listener);
    }

    @Override
    public void onBind(@NonNull final Setting setting,
                       final boolean enabled) {
        super.onBind(setting, enabled);

        final BooleanSetting s = (BooleanSetting) setting;

        vb.widget.setOnCheckedChangeListener(null);
        vb.widget.setChecked(s.isChecked());
        vb.widget.setOnCheckedChangeListener(changeListener);
    }
}
