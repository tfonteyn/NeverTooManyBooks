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

import java.util.Objects;

/**
 * The value is a {@code float}.
 * Use a step-size of {@code 1} for use as an {@code int}.
 * <p>
 * The UI will display this setting as a Slider.
 * <p>
 * Dev. note: technically it should be a {@code Float} object
 * allowing {@code null} for not-set.
 * But compatibility with SharedPreferences dictates not-set == {@code 0f}.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class FloatSetting
        extends Setting {

    private float valueFrom = 1;
    private float valueTo = 100;
    private float stepSize = 1;
    /**
     * Epsilon value to comparing two float values.
     *
     * @see #isValueEquals(float)
     */
    private float epsilon = 0.01f;

    private float value;

    FloatSetting(@NonNull final String key,
                 @NonNull final SettingsDataStore dataStore) {
        super(Type.Float, key, dataStore);
    }

    public float getValueFrom() {
        return valueFrom;
    }

    public void setValueFrom(final float valueFrom) {
        this.valueFrom = valueFrom;
    }

    public float getValueTo() {
        return valueTo;
    }

    public void setValueTo(final float valueTo) {
        this.valueTo = valueTo;
    }

    public float getStepSize() {
        return stepSize;
    }

    public void setStepSize(final float stepSize) {
        this.stepSize = stepSize;
    }

    public float getEpsilon() {
        return epsilon;
    }

    public void setEpsilon(final float epsilon) {
        this.epsilon = epsilon;
    }

    public float getValue() {
        return value;
    }

    public void setValue(final float value) {
        this.value = value;
    }

    public boolean isValueEquals(final float other) {
        return Math.abs(value - other) < epsilon;
    }

    @Override
    public void load(@NonNull final Context context) {
        setValue(dataStore.getFloat(getKey(), value));
    }

    @Override
    public void save(@NonNull final Context context) {
        dataStore.putFloat(getKey(), value);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FloatSetting that = (FloatSetting) o;
        // epsilon is not included
        return Float.compare(value, that.value) == 0
               && Float.compare(valueFrom, that.valueFrom) == 0
               && Float.compare(valueTo, that.valueTo) == 0
               && Float.compare(stepSize, that.stepSize) == 0;
    }

    @Override
    public int hashCode() {
        // epsilon is not included
        return Objects.hash(super.hashCode(), valueFrom, valueTo, stepSize, value);
    }
}
