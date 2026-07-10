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
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * The value is expected to be the list position.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
 */
public class StringArrayDropDownMenuField
        extends BaseField<Integer, AutoCompleteTextView> {

    @ArrayRes
    private final int arrayResId;

    /**
     * Constructor.
     *
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     * @param arrayResId  to use; the array <strong>must not</strong> be empty
     */
    public StringArrayDropDownMenuField(@IdRes final int fieldViewId,
                                        @NonNull final String fieldKey,
                                        @ArrayRes final int arrayResId) {
        super(fieldViewId, fieldKey, fieldKey);
        this.arrayResId = arrayResId;
    }

    /**
     * Set the id for the surrounding TextInputLayout (if this field has one).
     *
     * @param viewId view id
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public StringArrayDropDownMenuField setTextInputLayoutId(@IdRes final int viewId) {
        addRelatedViews(viewId);
        return this;
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);

        final AutoCompleteTextView view = requireView();

        final ExtArrayAdapter<CharSequence> adapter = ExtArrayAdapter.createFromResource(
                view.getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Passthrough, arrayResId);
        if ((long) adapter.getCount() <= 0) {
            throw new IllegalArgumentException("adapter.getCount()");
        }
        view.setAdapter(adapter);

        view.setOnItemClickListener((p, v, position, id) -> {
            final Integer previous = rawValue;
            rawValue = position;
            notifyIfChanged(previous);
        });
    }

    @Override
    @NonNull
    public Integer getValue() {
        return rawValue != null ? rawValue : 0;
    }

    @Override
    public void setValue(@Nullable final Integer value) {
        super.setValue(value != null ? value : 0);

        final AutoCompleteTextView view = getView();
        if (view != null) {
            //noinspection unchecked
            final ExtArrayAdapter<CharSequence> adapter =
                    (ExtArrayAdapter<CharSequence>) view.getAdapter();
            if (rawValue != null && rawValue >= 0 && rawValue < adapter.getCount()) {
                view.setText(adapter.getItem(rawValue), false);
            } else {
                view.setText(adapter.getItem(0), false);
            }
        }
    }

    @Override
    @Nullable
    public Integer load(@NonNull final Context context,
                        @NonNull final DataManager source,
                        @NonNull final RealNumberParser realNumberParser) {
        return source.getInt(getFieldKey());
    }

    @Override
    void save(@NonNull final DataManager target) {
        target.putInt(getFieldKey(), getValue());
    }

    @Override
    boolean isEmpty(@Nullable final Integer value) {
        return value == null || value == 0;
    }
}
