/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.properties;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Implement a String-based property with optional (non-empty) validation.
 *
 * @author Philip Warner
 */
public class StringProperty extends PropertyWithGlobalValue<String> {
    /** Options indicating value must be non-blank */
    private boolean mRequireNonBlank = false;

    public StringProperty(final @NonNull String uniqueId,
                          final @NonNull PropertyGroup group,
                          final @StringRes int nameResourceId,
                          final @Nullable String defaultValue) {
        super(uniqueId, group, nameResourceId, defaultValue);

    }

    /** Build the editor for this property */
    @Override
    @NonNull
    public View getView(final @NonNull LayoutInflater inflater) {
        final ViewGroup root = (ViewGroup) inflater.inflate(R.layout.row_property_string_in_place_editing, null);
        // create Holder
        Holder holder = new Holder();
        holder.property = this;
        holder.name = root.findViewById(R.id.name);
        holder.value = root.findViewById(R.id.value);

        // Set the initial values
        holder.name.setText(getNameResourceId());
        holder.value.setHint(getNameResourceId());
        holder.value.setText(getValue());

        // tags used
        ViewTagger.setTag(root, R.id.TAG_PROPERTY, holder);

        // Reflect all changes in underlying data
        holder.value.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(final @NonNull Editable s) {
                setValue(s.toString());
            }

            @Override
            public void beforeTextChanged(final CharSequence s,
                                          final int start, final int count, final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s,
                                      final int start, final int before, final int count) {
            }
        });

        return root;
    }


    /** Get underlying preferences value */
    @Override
    @Nullable
    protected String getGlobalValue() {
        return BookCatalogueApp.getStringPreference(getPreferenceKey(), getDefaultValue());
    }

    /** Set underlying preferences value */
    @Override
    @NonNull
    protected StringProperty setGlobalValue(final @Nullable String value) {
        BookCatalogueApp.getSharedPreferences().edit().putString(getPreferenceKey(), value).apply();
        return this;
    }


    /**
     * Validation if wanted
     */
    @NonNull
    public StringProperty setRequireNonBlank(final boolean requireNonBlank) {
        mRequireNonBlank = requireNonBlank;
        return this;
    }

    /** Optional validator. */
    @Override
    @CallSuper
    public void validate() throws ValidationException {
        if (mRequireNonBlank) {
            String s = getValue();
            if (s == null || s.trim().isEmpty()) {
                String fieldName = BookCatalogueApp.getResourceString(getNameResourceId());
                throw new ValidationException(BookCatalogueApp.getResourceString(R.string.warning_required_field_x, fieldName));
            }
        }
        super.validate();
    }

    @Override
    @NonNull
    @CallSuper
    public String toString() {
        return "StringProperty{" +
                "mRequireNonBlank=" + mRequireNonBlank +
                super.toString() +
                '}';
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @Override
    @NonNull
    @CallSuper
    public StringProperty setDefaultValue(final @Nullable String value) {
        super.setDefaultValue(value);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @Override
    @NonNull
    @CallSuper
    public StringProperty setWeight(final int weight) {
        super.setWeight(weight);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @Override
    @NonNull
    @CallSuper
    public StringProperty setGroup(final @NonNull PropertyGroup group) {
        super.setGroup(group);
        return this;
    }

    private static class Holder {
        StringProperty property;
        TextView name;
        EditText value;
    }
}

