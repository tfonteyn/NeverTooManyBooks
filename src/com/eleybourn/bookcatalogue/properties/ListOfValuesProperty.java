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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * Implement a generic list-of-values property.
 *
 * Resulting editing display is a list of values with Radio Buttons in a dialog.
 *
 * @param <T> type of the list items
 *
 * @author Philip Warner
 */
public abstract class ListOfValuesProperty<T> extends PropertyWithGlobalValue<T> {
    /** List of valid values */
    @NonNull
    private final ItemList<T> mList;

    /**
     * @param list list with options. Minimum 0 element; a 'use default' is added automatically.
     */
    ListOfValuesProperty(final @StringRes int nameResourceId,
                         final @NonNull PropertyGroup group,
                         final @NonNull T defaultValue,
                         final @NonNull @Size(min = 0) ItemList<T> list) {
        super(group, nameResourceId, defaultValue);
        mList = list;
    }

    /** Return the default list editor view with associated event handlers. */
    @NonNull
    @Override
    public View getView(final @NonNull LayoutInflater inflater) {
        final ViewGroup root = (ViewGroup) inflater.inflate(R.layout.row_property_list_of_values, null);

        // create Holder -> not needed here

        // tags used
        ViewTagger.setTag(root, R.id.TAG_PROPERTY, this);

        // Try to find the list item that corresponds to the current stored value.
        ListEntry<T> currentlyEntry = null;
        for (ListEntry<T> entry : mList) {
            if (entry.value == null) {
                if (getValue() == null) {
                    currentlyEntry = entry;
                }
            } else {
                if (getValue() != null && entry.value.equals(getValue())) {
                    currentlyEntry = entry;
                }
            }
        }

        // Set the initial values
        TextView text = root.findViewById(R.id.name);
        text.setText(getNameResourceId());
        setValueInView(root, currentlyEntry);

        // Setup click handlers for view and edit button
        root.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull View view) {
                handleClick(view, inflater);
            }
        });
        root.findViewById(R.id.btn_edit).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View /* the button view */ view) {
                // note we use the 'root' here. We're editing the property and not the "button"
                handleClick(root, inflater);
            }
        });

        return root;
    }

    /** Set the 'value' field in the passed view to match the passed item. */
    private void setValueInView(final @NonNull View baseView, final @Nullable ListEntry<T> item) {
        TextView view = baseView.findViewById(R.id.value);

        if (item == null) {
            view.setText("");
        } else {
            if (isDefault(item.value)) {
                view.setTypeface(null, Typeface.NORMAL);
            } else {
                view.setTypeface(null, Typeface.BOLD);
            }

            view.setText(item.getLabel());
        }
    }

    private void handleClick(final @NonNull View base, final @NonNull LayoutInflater inflater) {
        final ItemList<T> items = mList;
        if (this.hasHint()) {
            HintManager.displayHint(inflater, this.getHint(), new Runnable() {
                @Override
                public void run() {
                    displayList(base, inflater, items);
                }
            });
        } else {
            displayList(base, inflater, items);
        }
    }

    /**
     * Called to display a list of values for this property.
     *
     * @param baseView Specific view that was clicked
     * @param inflater LayoutInflater
     * @param items    All list items
     */
    private void displayList(final @NonNull View baseView,
                             final @NonNull LayoutInflater inflater,
                             final @NonNull ItemList<T> items) {

        T currentValue = this.getValue();

        // Get the view and the radio group
        @SuppressLint("InflateParams") // root==null as it's a dialog
                View root = inflater.inflate(R.layout.row_property_list_of_values_dialog, null);
        final AlertDialog dialog = new AlertDialog.Builder(inflater.getContext())
                .setView(root)
                .create();

        // Create a listener that responds to any click on the list
        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(@NonNull View v) {
                dialog.dismiss();
                Holder<T> holder = ViewTagger.getTagOrThrow(v);
                setValue(holder.item.value);
                setValueInView(holder.baseView, holder.item);
            }
        };

        RadioGroup radioGroup = root.findViewById(R.id.values);
        // Add each entry to the list
        for (ListEntry<T> entry : items) {
            // If we are looking at global-only values, NULL is invalid
            if (entry.value != null || !isGlobal()) {

                // Check if this value is the currently selected value
                boolean selected = false;
                if (entry.value == null && currentValue == null) {
                    selected = true;
                } else if (entry.value != null && entry.value.equals(currentValue)) {
                    selected = true;
                }

                // Make the view for this item
                View line = inflater.inflate(R.layout.row_property_list_of_values_item, radioGroup, false);
                CompoundButton sel = line.findViewById(R.id.selector);

                //Set the various values
                sel.setChecked(selected);
                sel.setText(entry.getLabel());

                // Listen for clicks
                sel.setOnClickListener(clickListener);

                // Set the tags used by the listeners
                ViewTagger.setTag(sel, new Holder<>(entry, baseView));

                // Add it to the group
                radioGroup.addView(line);
            }
        }

        dialog.show();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ListOfValuesProperty{" +
                "mList=[");

        for (ListEntry entry : mList) {
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
    public static class ListEntry<T> {
        /** Actual value, never displayed. */
        @Nullable
        final T value;
        /** Text description of the meaning of that value */
        @StringRes
        private int stringId;
        @Nullable
        private Object[] textArgs;

        /** Constructor. Instantiates string. */
        ListEntry(final @Nullable T value, final @StringRes int stringId, final @Nullable Object... args) {
            this.value = value;
            this.stringId = stringId;
            this.textArgs = args;
        }

        @NonNull
        String getLabel() {
            return BookCatalogueApp.getResourceString(stringId, textArgs);
        }

        @NonNull
        @Override
        public String toString() {
            return getLabel() + "=" + (value == null ? null : value.toString());
        }
    }

    /**
     * Class to represent a collection of list entries for a list-of-values property
     *
     * @param <T> Underlying ListEntry data type.
     *
     * @author Philip Warner
     */
    public static class ItemList<T> extends ArrayList<ListEntry<T>> {
        /**
         * Utility to make adding items easier.
         *
         * @param value    Underlying value
         * @param stringId String ID of description
         * @param args     for use with the stringId
         *
         * @return this for chaining
         */
        @NonNull
        public ItemList<T> add(final @Nullable T value,
                               final @StringRes int stringId,
                               final @NonNull Object... args) {
            add(new ListEntry<>(value, stringId, args));
            return this;
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
        final ListEntry<T> item;
        @NonNull
        final View baseView;

        Holder(final @NonNull ListEntry<T> item, final @NonNull View baseView) {
            this.item = item;
            this.baseView = baseView;
        }
    }
}

