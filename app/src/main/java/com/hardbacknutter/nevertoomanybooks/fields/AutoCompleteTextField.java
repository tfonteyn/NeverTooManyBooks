/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;


/**
 * The value is the text of the {@link AutoCompleteTextView}.
 * <p>
 * A {@code null} value is always handled as {@code ""}.
 */
public class AutoCompleteTextField
        extends EditTextField<String, AutoCompleteTextView> {

    /** The list for the adapter. */
    @NonNull
    private final Supplier<List<String>> listSupplier;

    /**
     * Constructor.
     *
     * @param fragmentId   the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId  the view id for this {@link Field}
     * @param fieldKey     Key used to access a {@link DataManager}
     * @param listSupplier Supplier with auto complete values
     */
    public AutoCompleteTextField(@NonNull final FragmentId fragmentId,
                                 @IdRes final int fieldViewId,
                                 @NonNull final String fieldKey,
                                 @NonNull final Supplier<List<String>> listSupplier) {
        super(fragmentId, fieldViewId, fieldKey);
        this.listSupplier = listSupplier;
    }

    /**
     * Constructor.
     *
     * @param fragmentId     the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId    the view id for this {@link Field}
     * @param fieldKey       Key used to access a {@link DataManager}
     * @param formatter      formatter to use
     * @param enableReformat flag: reformat after every user-change.
     * @param listSupplier   Supplier with auto complete values
     */
    public AutoCompleteTextField(@NonNull final FragmentId fragmentId,
                                 @IdRes final int fieldViewId,
                                 @NonNull final String fieldKey,
                                 @NonNull final FieldFormatter<String> formatter,
                                 final boolean enableReformat,
                                 @NonNull final Supplier<List<String>> listSupplier) {
        super(fragmentId, fieldViewId, fieldKey, formatter, enableReformat);
        this.listSupplier = listSupplier;
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);
        requireView().setAdapter(FieldArrayAdapter.createAutoComplete(
                parent.getContext(),
                listSupplier.get(),
                formatter));
    }

    @Override
    boolean isEmpty(@Nullable final String value) {
        return value == null || value.isEmpty();
    }
}
