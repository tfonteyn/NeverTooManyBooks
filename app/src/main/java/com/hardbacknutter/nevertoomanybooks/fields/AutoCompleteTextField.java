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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.util.List;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;


/**
 * The value is the text of the {@link AutoCompleteTextView}.
 * <p>
 * A {@code null} value is always handled as {@code ""}.
 */
public class AutoCompleteTextField
        extends EditTextField<String, AutoCompleteTextView> {

    /** The list for the adapter. */
    @NonNull
    private final Function<Context, List<String>> listSupplier;

    /**
     * Constructor.
     *
     * @param fieldViewId  the view id for this {@link Field}
     * @param fieldKey     Key used to access a {@link DataManager}
     * @param listSupplier Supplier with auto complete values
     */
    public AutoCompleteTextField(@IdRes final int fieldViewId,
                                 @NonNull final String fieldKey,
                                 @NonNull final Function<Context, List<String>> listSupplier) {
        super(fieldViewId, fieldKey);
        this.listSupplier = listSupplier;
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);
        final Context context = parent.getContext();
        requireView().setAdapter(FieldArrayAdapter.createAutoComplete(
                context,
                listSupplier.apply(context),
                getFormatter()));
    }
}
