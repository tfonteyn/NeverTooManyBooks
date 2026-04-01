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

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import com.hardbacknutter.prefslib.Setting;

final class ViewHolderFactory {

    private static final Map<Setting.Type, ViewHolderProvider> REGISTRY =
            new EnumMap<>(Setting.Type.class);

    static {
        REGISTRY.put(Setting.Type.Action, ActionViewHolder::create);
        REGISTRY.put(Setting.Type.Fragment, ActionViewHolder::create);
        REGISTRY.put(Setting.Type.Group, GroupViewHolder::create);
        REGISTRY.put(Setting.Type.Header, HeaderViewHolder::create);
        REGISTRY.put(Setting.Type.Boolean, SwitchViewHolder::create);
        REGISTRY.put(Setting.Type.String, StringViewHolder::create);
        REGISTRY.put(Setting.Type.Float, SliderViewHolder::create);
        REGISTRY.put(Setting.Type.SingleChoice, SingleChoiceViewHolder::create);
        REGISTRY.put(Setting.Type.MultiChoice, MultiChoiceViewHolder::create);
    }

    private ViewHolderFactory() {
    }

    @NonNull
    static SettingViewHolder create(@NonNull final Setting.Type type,
                                    @NonNull final ViewGroup parent,
                                    @NonNull final Function<Integer, Setting> itemProvider,
                                    @NonNull final UICallback listener) {
        final ViewHolderProvider provider = REGISTRY.get(type);

        if (provider == null) {
            throw new IllegalArgumentException("Unsupported view type: " + type);
        }

        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return provider.create(inflater, parent, itemProvider, listener);
    }

    @FunctionalInterface
    private interface ViewHolderProvider {

        @NonNull
        SettingViewHolder create(@NonNull LayoutInflater inflater,
                                 @NonNull ViewGroup parent,
                                 @NonNull Function<Integer, Setting> itemProvider,
                                 @NonNull UICallback listener);
    }
}
