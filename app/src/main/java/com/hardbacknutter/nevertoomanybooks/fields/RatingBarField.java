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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.widget.RatingBar;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * RatingBar.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
 */
public class RatingBarField
        extends BaseField<Float, RatingBar> {

    /**
     * Constructor.
     *
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     */
    public RatingBarField(@IdRes final int fieldViewId,
                          @NonNull final String fieldKey) {
        super(fieldViewId, fieldKey, fieldKey);
    }

    @Override
    @NonNull
    public Float getValue() {
        return rawValue != null ? rawValue : 0f;
    }

    @Override
    public void setValue(@Nullable final Float value) {
        super.setValue(value != null ? value : 0f);

        final RatingBar view = getView();
        if (view != null) {
            view.setRating(getValue());
        }
    }

    @Override
    @Nullable
    public Float load(@NonNull final Context context,
                      @NonNull final DataManager source,
                      @NonNull final RealNumberParser realNumberParser) {
        return source.getFloat(getFieldKey(), realNumberParser);
    }

    @Override
    void save(@NonNull final DataManager target) {
        target.putFloat(getFieldKey(), getValue());
    }

    @Override
    boolean isEmpty(@Nullable final Float value) {
        return value == null || value == 0f;
    }
}
