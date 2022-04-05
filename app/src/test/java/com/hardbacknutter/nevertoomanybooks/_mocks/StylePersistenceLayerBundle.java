/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks._mocks;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;

public class StylePersistenceLayerBundle
        implements StylePersistenceLayer {

    private final Bundle mBundle = ServiceLocator.newBundle();

    @Override
    public void remove(@NonNull final String key) {
        mBundle.remove(key);
    }

    @Override
    public void setString(@NonNull final String key,
                          @Nullable final String value) {
        mBundle.putString(key, value);
    }

    @Nullable
    @Override
    public String getNonGlobalString(@NonNull final String key) {
        return mBundle.getString(key);
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key) {
        return mBundle.getString(key);
    }

    @Override
    public void setBoolean(@NonNull final String key,
                           @Nullable final Boolean value) {
        if (value == null) {
            mBundle.remove(key);
        } else {
            mBundle.putBoolean(key, value);
        }
    }

    @Override
    public boolean getNonGlobalBoolean(@NonNull final String key) {
        return mBundle.getBoolean(key);
    }

    @Nullable
    @Override
    public Boolean getBoolean(@NonNull final String key) {
        if (mBundle.containsKey(key)) {
            return mBundle.getBoolean(key);
        } else {
            return null;
        }
    }

    @Override
    public void setInt(@NonNull final String key,
                       @Nullable final Integer value) {
        if (value == null) {
            mBundle.remove(key);
        } else {
            mBundle.putInt(key, value);
        }
    }

    @Nullable
    @Override
    public Integer getInteger(@NonNull final String key) {
        if (mBundle.containsKey(key)) {
            return mBundle.getInt(key);
        } else {
            return null;
        }
    }

    @Override
    public void setStringedInt(@NonNull final String key,
                               @Nullable final Integer value) {
        if (value == null) {
            mBundle.remove(key);
        } else {
            mBundle.putInt(key, value);
        }
    }

    @Nullable
    @Override
    public Integer getStringedInt(@NonNull final String key) {
        if (mBundle.containsKey(key)) {
            return mBundle.getInt(key);
        } else {
            return null;
        }
    }

    @Override
    public void setStringedIntList(@NonNull final String key,
                                   @Nullable final List<Integer> value) {
        if (value == null) {
            mBundle.remove(key);
        } else {
            mBundle.putIntegerArrayList(key, new ArrayList<>(value));
        }
    }

    @Nullable
    @Override
    public ArrayList<Integer> getStringedIntList(@NonNull final String key) {
        if (mBundle.containsKey(key)) {
            return mBundle.getIntegerArrayList(key);
        } else {
            return null;
        }
    }

    @Override
    public void setIntList(@NonNull final String key,
                           @Nullable final List<Integer> value) {
        if (value == null) {
            mBundle.remove(key);
        } else {
            mBundle.putIntegerArrayList(key, new ArrayList<>(value));
        }
    }

    @Nullable
    @Override
    public ArrayList<Integer> getIntList(@NonNull final String key) {
        if (mBundle.containsKey(key)) {
            return mBundle.getIntegerArrayList(key);
        } else {
            return null;
        }
    }

    @Override
    public void setBitmask(@NonNull final String key,
                           @Nullable final Integer value) {
        if (value == null) {
            mBundle.remove(key);
        } else {
            mBundle.putInt(key, value);
        }
    }

    @Nullable
    @Override
    public Integer getBitmask(@NonNull final String key) {
        if (mBundle.containsKey(key)) {
            return mBundle.getInt(key);
        } else {
            return null;
        }
    }
}
