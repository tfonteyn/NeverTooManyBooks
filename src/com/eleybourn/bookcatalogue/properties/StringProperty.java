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

import android.os.Parcel;
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

import java.util.Objects;

/**
 * Implement a String-based property with optional (non-empty) validation.
 *
 * @author Philip Warner
 */
public class StringProperty extends PropertyWithGlobalValue<String> {
    /** Options indicating value must be non-blank */
    private boolean mRequireNonBlank = false;

    public StringProperty(final @StringRes int nameResourceId,
                          final @NonNull PropertyGroup group,
                          final @NonNull String defaultValue) {
        super(group, nameResourceId, defaultValue);
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
        holder.value.setHint(getResolvedValue());
        holder.value.setText(getResolvedValue());

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
    @NonNull
    protected String getGlobalValue() {
        //noinspection ConstantConditions
        return BookCatalogueApp.getStringPreference(getPreferenceKey(), getDefaultValue());
    }

    /** Set underlying preferences value */
    @Override
    @NonNull
    protected StringProperty setGlobalValue(final @Nullable String value) {
        Objects.requireNonNull(value);
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
            // check actual (non-resolved)
            String s = getValue();
            if (s == null || s.trim().isEmpty()) {
                String fieldName = BookCatalogueApp.getResourceString(getNameResourceId());
                throw new ValidationException(BookCatalogueApp.getResourceString(R.string.warning_required_field_x, fieldName));
            }
        }
        super.validate();
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @Override
    @NonNull
    @CallSuper
    public StringProperty setDefaultValue(final @NonNull String value) {
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

    /**
     * Our value is Nullable. To Parcel it, we use Integer.MIN_VALUE for null.
     *
     * Note that {@link Parcel#writeValue(Object)} actually writes an Integer as 'int'
     * So 'null' is NOT preserved.
     */
    public void writeToParcel(final @NonNull Parcel dest) {
        String value = this.getValue();
        dest.writeString(value);
    }

    public void readFromParcel(final @NonNull Parcel in) {
        String parceledString = in.readString();
        setValue(parceledString);
    }
    private static class Holder {
        StringProperty property;
        TextView name;
        EditText value;
    }
}

