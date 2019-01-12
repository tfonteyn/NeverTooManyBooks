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
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.Prefs;
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

    public StringProperty(@StringRes final int nameResourceId,
                          @NonNull final PropertyGroup group,
                          @NonNull final String defaultValue) {
        super(group, nameResourceId, defaultValue);
    }

    /** Build the editor for this property */
    @Override
    @NonNull
    public View getView(@NonNull final LayoutInflater inflater) {
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
            public void beforeTextChanged(@NonNull final CharSequence s,
                                          final int start, final int count, final int after) {
            }

            @Override
            public void onTextChanged(@NonNull final CharSequence s,
                                      final int start, final int before, final int count) {
            }

            @Override
            public void afterTextChanged(@NonNull final Editable s) {
                setValue(s.toString());
            }
        });

        return root;
    }

    /** Get underlying preferences value */
    @Override
    @NonNull
    protected String getGlobalValue() {
        //noinspection ConstantConditions
        return Prefs.getPrefs().getString(getPreferenceKey(), getDefaultValue());
    }

    /** Set underlying preferences value */
    @Override
    @NonNull
    protected StringProperty setGlobalValue(@Nullable final String value) {
        Objects.requireNonNull(value);
        Prefs.getPrefs().edit().putString(getPreferenceKey(), value).apply();
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
                String fieldName = BookCatalogueApp.getResString(getNameResourceId());
                throw new ValidationException(BookCatalogueApp.getResString(R.string.warning_required_field_x, fieldName));
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
    public StringProperty setDefaultValue(@NonNull final String value) {
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
    public StringProperty setGroup(@NonNull final PropertyGroup group) {
        super.setGroup(group);
        return this;
    }

    public void writeToParcel(@NonNull final Parcel dest) {
        String value = this.getValue();
        dest.writeString(value);
    }

    public void readFromParcel(@NonNull final Parcel in) {
        String parceledString = in.readString();
        setValue(parceledString);
    }
    private static class Holder {
        StringProperty property;
        TextView name;
        EditText value;
    }
}

