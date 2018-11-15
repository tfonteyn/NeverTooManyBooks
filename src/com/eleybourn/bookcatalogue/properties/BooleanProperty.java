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
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.Property.BooleanValue;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.Objects;

/**
 * Extends ValuePropertyWithGlobalDefault to create a trinary value (or nullable boolean?) with
 * associated editing support.
 *
 * Resulting editing display is a checkbox that cycles between 3 values.
 *
 * @author Philip Warner
 */
public class BooleanProperty extends ValuePropertyWithGlobalDefault<Boolean> implements BooleanValue {

    public BooleanProperty(final @NonNull String uniqueId,
                           final @NonNull PropertyGroup group,
                           final @StringRes int nameResourceId) {
        super(uniqueId, group, nameResourceId, false);
    }

    @NonNull
    @Override
    public View getView(final @NonNull LayoutInflater inflater) {
        final ViewGroup root = (ViewGroup) inflater.inflate(R.layout.row_property_boolean, null);

        // create Holder
        final Holder holder = new Holder();
        holder.property = this;
        holder.cb = root.findViewById(R.id.checkbox);
        // Set the ID so weird stuff does not happen on activity reload after config changes.
        holder.cb.setId(nextViewId());
        holder.name = root.findViewById(R.id.filename);
        holder.name.setText(this.getNameResourceId());
        holder.value = root.findViewById(R.id.value);

        // tags used
        ViewTagger.setTag(root, R.id.TAG_PROPERTY, holder);// value BooleanProperty.Holder
        ViewTagger.setTag(holder.cb, R.id.TAG_PROPERTY, holder);// value BooleanProperty.Holder

        // Set the initial values
        setViewValues(holder, get());

        // Setup click handlers for view and checkbox
        root.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull View v) {
                handleClick(v);
            }
        });
        holder.cb.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull View v) {
                handleClick(v);
            }
        });

        return root;
    }

    private void handleClick(final @NonNull View view) {
        Holder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_PROPERTY);// value BooleanProperty.Holder
        Boolean value = holder.property.get();
        // Cycle through three values: 'null', 'true', 'false'. If the value is 'global' omit 'null'.
        if (value == null) {
            value = true;
        } else if (value) {
            value = false;
        } else {
            if (isGlobal()) {
                value = true;
            } else {
                value = null;
            }
        }
        holder.property.set(value);
        holder.property.setViewValues(holder, value);
    }

    /** Set the checkbox and text fields based on passed value */
    private void setViewValues(final @NonNull Holder holder, final @Nullable Boolean value) {
        if (value != null) {
            // We have a value, so setup based on it
            holder.cb.setChecked(value);
            holder.name.setText(this.getNameResourceId());
            holder.value.setText(value ? R.string.yes : R.string.no);
            holder.cb.setPressed(false);
        } else {
            // Null value; use defaults.
            holder.cb.setChecked(isTrue());
            holder.name.setText(this.getName());
            holder.value.setText(R.string.use_default_setting);
            holder.cb.setPressed(false);
        }
    }

    @Override
    @NonNull
    protected Boolean getGlobalDefault() {
        return BookCatalogueApp.Prefs.getBoolean(getPreferenceKey(), Objects.requireNonNull(getDefaultValue()));
    }

    @Override
    @Nullable
    protected BooleanProperty setGlobalDefault(final @Nullable Boolean value) {
        Objects.requireNonNull(value);
        BookCatalogueApp.Prefs.putBoolean(getPreferenceKey(), value);
        return this;
    }

    @NonNull
    @Override
    public BooleanProperty set(final @NonNull Property p) {
        if (!(p instanceof BooleanValue)) {
            throw new RTE.IllegalTypeException(p.getClass().getCanonicalName());
        }
        BooleanValue bv = (BooleanValue) p;
        set(bv.get());
        return this;
    }

    public boolean isTrue() {
        Boolean b =  super.getResolvedValue();
        return (b != null ? b: false);
    }


    @Override
    @NonNull
    @CallSuper
    public BooleanProperty setGlobal(boolean isGlobal) {
        super.setGlobal(isGlobal);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanProperty setDefaultValue(final @Nullable Boolean value) {
        Objects.requireNonNull(value);
        super.setDefaultValue(value);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanProperty setGroup(final @NonNull PropertyGroup group) {
        super.setGroup(group);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanProperty setWeight(int weight) {
        super.setWeight(weight);
        return this;
    }

    @Override
    @NonNull
    @CallSuper
    public BooleanProperty setPreferenceKey(final @NonNull String key) {
        super.setPreferenceKey(key);
        return this;
    }

    private static class Holder {
        CompoundButton cb;
        TextView name;
        TextView value;
        BooleanProperty property;
    }
}

