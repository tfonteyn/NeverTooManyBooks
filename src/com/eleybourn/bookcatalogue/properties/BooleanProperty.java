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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.Property.BooleanValue;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

/**
 * Extends ValuePropertyWithGlobalDefault to create a trinary value (or nullable boolean?) with
 * associated editing support.
 *
 * Resulting editing display is a checkbox that cycles between 3 values.
 *
 * @author Philip Warner
 */
public class BooleanProperty extends ValuePropertyWithGlobalDefault<Boolean> implements BooleanValue {


    public BooleanProperty(@NonNull final String uniqueId,
                           @NonNull final PropertyGroup group,
                           final int nameResourceId) {
        super(uniqueId, group, nameResourceId, false);
    }

    @NonNull
    @Override
    public View getView(@NonNull final LayoutInflater inflater) {
        // Get the view and setup holder
        View v = inflater.inflate(R.layout.property_value_boolean, null);
        final Holder h = new Holder();

        h.property = this;
        h.cb = v.findViewById(R.id.checkbox);
        h.name = v.findViewById(R.id.name);
        h.value = v.findViewById(R.id.value);

        ViewTagger.setTag(v, R.id.TAG_PROPERTY, h);
        ViewTagger.setTag(h.cb, R.id.TAG_PROPERTY, h);

        // Set the ID so weird stuff does not happen on activity reload after config changes.
        h.cb.setId(nextViewId());

        h.name.setText(this.getNameResourceId());

        // Set initial checkbox state
        Boolean b = get();
        setViewValues(h, b);

        // Setup click handlers for view and checkbox
        h.cb.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick(v);
            }
        });

        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick(v);
            }
        });

        return v;
    }

    private void handleClick(@NonNull final View v) {
        Holder holder = ViewTagger.getTag(v, R.id.TAG_PROPERTY);
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

    /** Set the checkbox and text fields based on passed value. */
    private void setViewValues(@NonNull final Holder holder, @Nullable final Boolean value) {
        if (value != null) {
            // We have a value, so setup based on it
            holder.cb.setChecked(value);
            holder.name.setText(this.getNameResourceId());
            if (value) {
                holder.value.setText(R.string.yes);
            } else {
                holder.value.setText(R.string.no);
            }
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
        Boolean b = getDefaultValue();
        if (b == null) {
            throw new IllegalStateException();
        }
        return BCPreferences.getBoolean(getPreferenceKey(), b);
    }

    @Override
    @Nullable
    protected BooleanProperty setGlobalDefault(@Nullable final Boolean value) {
        if (value == null) {
            throw new IllegalStateException();
        }
        BCPreferences.setBoolean(getPreferenceKey(), value);
        return this;
    }

    @NonNull
    @Override
    public BooleanProperty set(@NonNull final Property p) {
        if (!(p instanceof BooleanValue)) {
            throw new IllegalStateException();
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
    public BooleanProperty setGlobal(boolean isGlobal) {
        super.setGlobal(isGlobal);
        return this;
    }

    @NonNull
    @Override
    public BooleanProperty setDefaultValue(@Nullable final Boolean value) {
        if (value == null) {
            throw new IllegalStateException();
        }
        super.setDefaultValue(value);
        return this;
    }

    @NonNull
    @Override
    public BooleanProperty setGroup(@NonNull final PropertyGroup group) {
        super.setGroup(group);
        return this;
    }

    @NonNull
    @Override
    public BooleanProperty setWeight(int weight) {
        super.setWeight(weight);
        return this;
    }

    @Override
    @NonNull
    public BooleanProperty setPreferenceKey(@NonNull final String key) {
        super.setPreferenceKey(key);
        return this;
    }

    private static class Holder {
        CheckBox cb;
        TextView name;
        TextView value;
        BooleanProperty property;
    }
}

