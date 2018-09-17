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

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

/**
 * Implement a generic list-of-values property. Display is done with Radio Buttons
 *
 * @param <T> Base type of list items
 *
 * @author Philip Warner
 */
public abstract class ListProperty<T> extends ValuePropertyWithGlobalDefault<T> {
    /** List of valid values */
    private final ItemEntries<T> mList;

    ListProperty(@NonNull final ItemEntries<T> list,
                 @NonNull final String uniqueId,
                 @NonNull final PropertyGroup group,
                 final int nameResourceId,
                 @Nullable final T defaultValue,
                 @SuppressWarnings("SameParameterValue") @Nullable final String defaultPref,
                 @Nullable final T value) {
        super(uniqueId, group, nameResourceId, defaultValue, defaultPref, value);
        mList = list;
    }

    @SuppressWarnings("WeakerAccess")
    public ItemEntries<T> getListItems() {
        return mList;
    }

    /** Return the default list editor view with associated event handlers. */
    @Override
    public View getView(final LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.property_value_list, null);
        ViewTagger.setTag(view, R.id.TAG_PROPERTY, this);
        // Display the list of values when clicked.
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick(v, inflater);
            }
        });

        // Set the name
        TextView text = view.findViewById(R.id.name);
        text.setText(getName());

        // Try to find the list item that corresponds to the current stored value.
        ItemEntry<T> entry = null;
        for (ItemEntry<T> e : getListItems()) {
            if (e.value == null) {
                if (get() == null) {
                    entry = e;
                }
            } else {
                if (get() != null && get().equals(e.value)) {
                    entry = e;
                }
            }
        }
        // Display current value
        setValueInView(view, entry);

        return view;
    }

    private void handleClick(final View base, final LayoutInflater inflater) {
        final ItemEntries<T> items = getListItems();
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
    private void setValueInView(View baseView, ItemEntry<T> item) {
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
    private void displayList(final View baseView, final LayoutInflater inflater, ItemEntries<T> items) {

        T currentValue = this.get();

        // Get the view and the radio group
        View root = inflater.inflate(R.layout.property_value_list_list, null);
        final AlertDialog dialog = new AlertDialog.Builder(inflater.getContext())
                .setView(root)
                .create();

        // Create a listener that responds to any click on the list
        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Holder<T> holder = ViewTagger.getTag(v, R.id.TAG_HOLDER);
                set(Objects.requireNonNull(holder).item.value);
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
                else if (entry.value != null && currentValue != null && entry.value.equals(currentValue))
                    selected = true;

                // Make the view for this item
                View line = inflater.inflate(R.layout.property_value_list_item, null);
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
        final T value;
        /** Test description of the meaning of that value */
        int textId;
        Object[] textArgs;

        /** Constructor. Instantiates string. */
        ItemEntry(T value, int resourceId, Object... args) {
            this.value = value;
            this.textId = resourceId; //BookCatalogueApp.getResourceString(resourceId);
            this.textArgs = args;
        }

        public String getString() {
            return BookCatalogueApp.getResourceString(textId, textArgs);
        }

        public T getValue() {
            return value;
        }

        public void setString(int value, Object... args) {
            textId = value;
            textArgs = args;
        }

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
        final ArrayList<ItemEntry<T>> mList = new ArrayList<>();

        /**
         * Utility to make adding items easier.
         *
         * @param value    Underlying value
         * @param stringId String ID of description
         *
         * @return this for chaining
         */
        public ItemEntries<T> add(T value, int stringId, Object... args) {
            mList.add(new ItemEntry<>(value, stringId, args));
            return this;
        }

//		/**
//		 * Utility to make adding items easier.
//		 *
//		 * @param value		Underlying value
//		 * @param stringId	Description
//		 * @return
//		 */
//		public ItemEntries<T> add(T value, int string) {
//			mList.add(new ItemEntry<T>(value, string));
//			return this;
//		}

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
        final ItemEntry<T> item;
        final View baseView;

        Holder(ItemEntry<T> item, View baseView) {
            this.item = item;
            this.baseView = baseView;
        }
    }
}

