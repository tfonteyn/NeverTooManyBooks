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

package com.hardbacknutter.prefslib;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

public class SettingsGroup
        extends Setting {

    @NonNull
    private final List<Setting> subSettings;

    SettingsGroup(@NonNull final String key,
                  @NonNull final List<Setting> subSettings) {
        super(Type.Group, key);
        this.subSettings = subSettings;
    }

    @NonNull
    public List<Setting> getSubSettings() {
        return subSettings;
    }

    @Override
    public void load(@NonNull final Context context,
                     @NonNull final SettingsDataStore store) {
        subSettings.forEach(setting -> setting.load(context, store));
    }

    @Override
    public void save(@NonNull final Context context,
                     @NonNull final SettingsDataStore store) {
        subSettings.forEach(setting -> setting.save(context, store));
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SettingsGroup that = (SettingsGroup) o;
        return Objects.equals(subSettings, that.subSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), subSettings);
    }
}
