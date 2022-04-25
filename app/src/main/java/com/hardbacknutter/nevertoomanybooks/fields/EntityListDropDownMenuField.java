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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;

/**
 * A {@code null} value is always handled as the 0'th element in the list.
 */
public class EntityListDropDownMenuField<T extends Entity>
        extends BaseField<Long, AutoCompleteTextView> {

    @NonNull
    private final EntityArrayAdapter<T> mAdapter;
    @NonNull
    private final List<T> mItems;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public EntityListDropDownMenuField(@NonNull final FragmentId fragmentId,
                                       @IdRes final int fieldViewId,
                                       @NonNull final String fieldKey,
                                       @NonNull final Context context,
                                       @NonNull final List<T> items) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey);
        mItems = items;
        mAdapter = new EntityArrayAdapter<>(context, items);

        SanityCheck.requirePositiveValue(mAdapter.getCount(), "mAdapter.getCount()");
    }

    @NonNull
    public EntityListDropDownMenuField<T> setTextInputLayoutId(@IdRes final int viewId) {
        addRelatedViews(viewId);
        return this;
    }

    @Override
    public void setParentView(@NonNull final View parent,
                              @NonNull final SharedPreferences global) {
        super.setParentView(parent, global);

        final AutoCompleteTextView view = requireView();
        view.setAdapter(mAdapter);
        view.setOnItemClickListener((p, v, position, id) -> {
            final Long previous = mRawValue;
            mRawValue = mItems.get(position).getId();
            notifyIfChanged(previous);
        });
    }

    @Override
    @NonNull
    public Long getValue() {
        return mRawValue != null ? mRawValue : mItems.get(0).getId();
    }

    @Override
    public void setValue(@Nullable final Long value) {
        super.setValue(value != null ? value : mItems.get(0).getId());

        final AutoCompleteTextView view = getView();
        if (view != null) {
            //noinspection ConstantConditions
            final T current = mItems.stream()
                                    .filter(item -> item.getId() == mRawValue)
                                    .findFirst()
                                    .orElse(mItems.get(0));
            view.setText(current.getLabel(view.getContext()), false);
        }
    }

    @Override
    public void setInitialValue(@NonNull final DataManager source) {
        mInitialValue = source.getLong(mFieldKey);
        setValue(mInitialValue);
    }

    @Override
    void internalPutValue(@NonNull final DataManager target) {
        target.putLong(mFieldKey, getValue());
    }

    @Override
    boolean isEmpty(@Nullable final Long value) {
        return value == null || value == mItems.get(0).getId();
    }
}
