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
import com.eleybourn.bookcatalogue.properties.Property.StringValue;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Implement a String-based property with optional (non-empty) validation.
 *
 * @author Philip Warner
 */
public class StringProperty extends ValuePropertyWithGlobalDefault<String> implements StringValue {
    /** Options indicating value must be non-blank */
    private boolean mRequireNonBlank = false;

    public StringProperty(final @NonNull String uniqueId,
                          final @NonNull PropertyGroup group,
                          final @StringRes int nameResourceId) {
        super(uniqueId, group, nameResourceId, "");
    }

    /** Build the editor for this property */
    @Override
    @NonNull
    public View getView(final @NonNull LayoutInflater inflater) {
        final ViewGroup root = (ViewGroup) inflater.inflate(R.layout.row_property_string_in_place_editing, null);
        // create Holder -> not needed here

        // tags used
        ViewTagger.setTag(root, R.id.TAG_PROPERTY, this);// value: StringProperty

        // Set the initial values
        final TextView name = root.findViewById(R.id.name);
        name.setText(getName());
        final EditText value = root.findViewById(R.id.value);
        value.setHint(getName());
        value.setText(get());

        // Reflect all changes in underlying data
        value.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(final @NonNull Editable s) {
                set(s.toString());
            }

            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            }
        });

        return root;
    }

    @NonNull
    public StringProperty setRequireNonBlank(final boolean requireNonBlank) {
        mRequireNonBlank = requireNonBlank;
        return this;
    }

    /** Get underlying preferences value */
    @Override
    @Nullable
    protected String getGlobalDefault() {
        return BookCatalogueApp.getStringPreference(getPreferenceKey(), getDefaultValue());
    }

    /** Set underlying preferences value */
    @Override
    @NonNull
    protected StringProperty setGlobalDefault(final @Nullable String value) {
        BookCatalogueApp.getSharedPreferences().edit().putString(getPreferenceKey(), value).apply();
        return this;
    }

    @Override
    @NonNull
    @CallSuper
    public StringProperty setDefaultValue(final @Nullable String value) {
        super.setDefaultValue(value);
        return this;
    }

    @Override
    @NonNull
    @CallSuper
    public StringProperty setWeight(final int weight) {
        super.setWeight(weight);
        return this;
    }

    @Override
    @NonNull
    @CallSuper
    public StringProperty setGroup(final @NonNull PropertyGroup group) {
        super.setGroup(group);
        return this;
    }

    @Override
    @NonNull
    public StringProperty set(final @NonNull Property p) {
        if (!(p instanceof StringValue)) {
            throw new RTE.IllegalTypeException(p.getClass().getCanonicalName());
        }
        StringValue bv = (StringValue) p;
        set(bv.get());
        return this;
    }

    /** Optional validator. */
    @Override
    public void validate() throws ValidationException {
        if (mRequireNonBlank) {
            String s = get();
            if (s == null || s.trim().isEmpty()) {
                throw new ValidationException(BookCatalogueApp.getResourceString(R.string.warning_required_field_x, getName()));
            }
        }
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
}

