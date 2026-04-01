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

import com.google.android.material.slider.Slider;

import java.util.function.Function;

import com.hardbacknutter.prefslib.FloatSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.databinding.PrefsLibSliderBinding;

public final class SliderViewHolder
        extends SettingViewHolder {

    @NonNull
    private final com.hardbacknutter.prefslib.databinding.PrefsLibSliderBinding vb;

    private SliderViewHolder(@NonNull final PrefsLibSliderBinding vb,
                             @NonNull final Function<Integer, Setting> itemProvider,
                             @NonNull final UICallback listener) {
        super(vb.getRoot(), vb.icon, vb.title, vb.summary);
        this.vb = vb;

        vb.slider.addOnChangeListener((slider, value, fromUser) -> {
            final String text;
            // Cut of the decimal part if it's zero
            if (value % 1 == 0) {
                text = String.valueOf((int) value);
            } else {
                text = String.valueOf(value);
            }
            vb.sliderValue.setText(text);
        });

        vb.slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull final Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull final Slider slider) {
                final int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }

                final FloatSetting setting = (FloatSetting) itemProvider.apply(position);
                final float newValue = slider.getValue();
                listener.onChange(setting, newValue);
            }
        });
    }

    @NonNull
    static SliderViewHolder create(@NonNull final LayoutInflater inflater,
                                   @NonNull final ViewGroup parent,
                                   @NonNull final Function<Integer, Setting> itemProvider,
                                   @NonNull final UICallback listener) {
        final PrefsLibSliderBinding vb = PrefsLibSliderBinding
                .inflate(inflater, parent, false);
        return new SliderViewHolder(vb, itemProvider, listener);
    }

    @Override
    public void onBind(@NonNull final Setting setting,
                       final boolean enabled) {
        super.onBind(setting, enabled);

        final FloatSetting s = (FloatSetting) setting;

        vb.slider.setValueFrom(s.getValueFrom());
        vb.slider.setValueTo(s.getValueTo());
        vb.slider.setStepSize(s.getStepSize());

        vb.slider.setValue(s.getValue());
    }
}
