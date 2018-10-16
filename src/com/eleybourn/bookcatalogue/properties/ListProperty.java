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

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implement a generic list-of-values property. Display is done with Radio Buttons
 *
 * @param <T> Base type of list items
 *
 * @author Philip Warner
 */
public abstract class ListProperty<T> extends ValuePropertyWithGlobalDefault<T> {
    /** List of valid values */
    @NonNull
    private final ItemEntries<T> mList;

    ListProperty(@NonNull final ItemEntries<T> list,
                 @NonNull final String uniqueId,
                 @NonNull final PropertyGroup group,
                 @StringRes final int nameResourceId) {
        super(uniqueId, group, nameResourceId);
        mList = list;
    }

    ListProperty(@NonNull final ItemEntries<T> list,
                 @NonNull final String uniqueId,
                 @NonNull final PropertyGroup group,
                 @StringRes final int nameResourceId,
                 @SuppressWarnings("SameParameterValue") @Nullable final T defaultValue,
                 @SuppressWarnings("SameParameterValue") @Nullable final T value) {
        super(uniqueId, group, nameResourceId, defaultValue, value);
        mList = list;
    }

    /** Return the default list editor view with associated event handlers. */
    @NonNull
    @Override
    public View getView(@NonNull final LayoutInflater inflater) {
        final ViewGroup root = (ViewGroup) inflater.inflate(R.layout.property_value_list, null);
        ViewTagger.setTag(root, R.id.TAG_PROPERTY, this);
        // Display the list of values when clicked.
        root.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull View view) {
                handleClick(view, inflater);
            }
        });

        // Set the name
        TextView text = root.findViewById(R.id.name);
        text.setText(getName());

        // Try to find the list item that corresponds to the current stored value.
        ItemEntry<T> entry = null;
        for (ItemEntry<T> e : mList) {
            if (e.value == null) {
                if (get() == null) {
                    entry = e;
                }
            } else {
                if (get() != null && e.value.equals(get())) {
                    entry = e;
                }
            }
        }
        // Display current value
        setValueInView(root, entry);

        return root;
    }

    private void handleClick(@NonNull final View base, @NonNull final LayoutInflater inflater) {
        final ItemEntries<T> items = mList;
        if (this.hasHint()) {
            HintManager.displayHint(base.getContext(), this.getHint(), new Runnable() {
                @Override
                public void run() {
                    displayList(base, inflater, items);
                }
            });
        } else {
            displayList(base, inflater, items);
        }
    }

    /** Set the 'value' field in the passed view to match the passed item. */
    private void setValueInView(@NonNull final View baseView, @Nullable final ItemEntry<T> item) {
        TextView text = baseView.findViewById(R.id.value);

        if (item == null) {
            text.setText("");
        } else {
            if (isDefault(item.value))
                text.setTypeface(null, Typeface.NORMAL);
            else
                text.setTypeface(null, Typeface.BOLD);

            text.setText(item.getString());
        }
    }

    /**
     * Called to display a list of values for this property.
     *
     * @param baseView Specific view that was clicked
     * @param inflater LayoutInflater
     * @param items    All list items
     */
    private void displayList(@NonNull final View baseView,
                             @NonNull final LayoutInflater inflater,
                             @NonNull final ItemEntries<T> items) {

        T currentValue = this.get();

        // Get the view and the radio group
        @SuppressLint("InflateParams") // dialog, no root
                View root = inflater.inflate(R.layout.property_value_list_list, null);
        final AlertDialog dialog = new AlertDialog.Builder(inflater.getContext())
                .setView(root)
                .create();

        // Create a listener that responds to any click on the list
        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(@NonNull View v) {
                dialog.dismiss();
                Holder<T> holder = ViewTagger.getTagOrThrow(v, R.id.TAG_HOLDER);
                set(holder.item.value);
                setValueInView(holder.baseView, holder.item);
            }
        };

        RadioGroup radioGroup = root.findViewById(R.id.values);
        // Add each entry to the list
        for (ItemEntry<T> entry : items) {
            // If we are looking at global-only values, NULL is invalid
            if (entry.value != null || !isGlobal()) {

                // Check if this value is the currently selected value
                boolean selected = false;
                if (entry.value == null && currentValue == null)
                    selected = true;
                else if (entry.value != null && entry.value.equals(currentValue))
                    selected = true;

                // Make the view for this item
                View line = inflater.inflate(R.layout.property_value_list_item, radioGroup, false);
                RadioButton sel = line.findViewById(R.id.selector);

                //Set the various values
                sel.setChecked(selected);
                sel.setText(entry.getString());

                // Listen for clicks
                sel.setOnClickListener(clickListener);

                // Set the tacks used by the listeners
                ViewTagger.setTag(sel, R.id.TAG_HOLDER, new Holder<>(entry, baseView));

                // Add it to the group
                radioGroup.addView(line);
            }
        }

        dialog.show();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ListProperty{" +
                "mList=[");

        for (ItemEntry entry : mList) {
            sb.append("{").append(entry).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Class to represent all items in a list-of-values property
     *
     * @param <T> Type of underlying list item
     *
     * @author Philip Warner
     */
    public static class ItemEntry<T> {
        /** Actual value */
        @Nullable
        final T value;
        /** Test description of the meaning of that value */
        int stringId;
        Object[] textArgs;

        /** Constructor. Instantiates string. */
        ItemEntry(@Nullable final T value, @StringRes final int stringId, final Object... args) {
            this.value = value;
            this.stringId = stringId;
            this.textArgs = args;
        }

        @NonNull
        public String getString() {
            return BookCatalogueApp.getResourceString(stringId, textArgs);
        }

        @Nullable
        public T getValue() {
            return value;
        }

        public void setString(int value, Object... args) {
            stringId = value;
            textArgs = args;
        }

        @NonNull
        @Override
        public String toString() {
            return getString();
        }
    }

    /**
     * Class to represent a collection of list entries for a list-of-values property
     *
     * @param <T> Underlying list item data type.
     *
     * @author Philip Warner
     */
    public static class ItemEntries<T> implements Iterable<ItemEntry<T>> {
        final List<ItemEntry<T>> mList = new ArrayList<>();

        /**
         * Utility to make adding items easier.
         *
         * @param value    Underlying value
         * @param stringId String ID of description
         *
         * @return this for chaining
         */
        @NonNull
        public ItemEntries<T> add(@Nullable final T value, @StringRes final int stringId, @NonNull final Object... args) {
            mList.add(new ItemEntry<>(value, stringId, args));
            return this;
        }

        @NonNull
        @Override
        public Iterator<ItemEntry<T>> iterator() {
            return mList.iterator();
        }
    }

    /**
     * Holder class for list items
     *
     * @param <T>
     *
     * @author Philip Warner
     */
    private static class Holder<T> {
        @NonNull
        final ItemEntry<T> item;
        @NonNull
        final View baseView;

        Holder(@NonNull final ItemEntry<T> item, @NonNull final View baseView) {
            this.item = item;
            this.baseView = baseView;
        }
    }
}

