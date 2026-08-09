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

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.Objects;
import java.util.function.Supplier;

public class FragmentSetting
        extends ActionSetting {

    @NonNull
    private final String className;
    private final int container;

    @Nullable
    private Supplier<Bundle> argumentSupplier;

    FragmentSetting(@NonNull final String key,
                    @NonNull final String className,
                    @IdRes final int container,
                    @NonNull final SettingsDataStore dataStore) {
        super(Type.Fragment, key, dataStore);
        this.className = className;
        this.container = container;
    }

    public void setArgumentSupplier(@Nullable final Supplier<Bundle> argumentSupplier) {
        this.argumentSupplier = argumentSupplier;
    }

    public void start(@NonNull final FragmentManager fm,
                      @NonNull final ClassLoader classLoader) {

        final Fragment fragment = fm.getFragmentFactory().instantiate(classLoader, className);

        if (argumentSupplier != null) {
            fragment.setArguments(argumentSupplier.get());
        }

        fm.beginTransaction()
          .setReorderingAllowed(true)
          .addToBackStack(fragment.getTag())
          .replace(container, fragment)
          .commit();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FragmentSetting that = (FragmentSetting) o;
        return container == that.container
               && Objects.equals(className, that.className)
               && Objects.equals(argumentSupplier, that.argumentSupplier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), className, container, argumentSupplier);
    }
}
