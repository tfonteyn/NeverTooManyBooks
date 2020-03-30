/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.dialogs.picker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Provides an AlertDialog with an optional title and message.
 * The content is a list of strings suited to populate the {@link Field}.
 *
 * @param <T> type of the actual Object that is represented by a row in the selection list.
 *            Right now, this is ALWAYS {@code String} and we use {@link #toString()}.
 *            Using another type is bound to bring up issues.
 */
public class FieldPicker<T>
        extends ValuePicker {

    /**
     * Value picker for a text field.
     *
     * @param context Current context
     * @param title   Dialog title
     * @param field   to get/set
     * @param list    list to choose from
     */
    private FieldPicker(@NonNull final Context context,
                        @Nullable final CharSequence title,
                        @NonNull final Field<T> field,
                        @NonNull final List<T> list) {
        super(context, title, null, false);

        FieldListAdapter<T> adapter = new FieldListAdapter<>(context, field, list, item -> {
            dismiss();
            // Update, display and notify
            field.getAccessor().setValue(item);
            field.onChanged(true);
        });
        setAdapter(adapter, adapter.getPreSelectedPosition());
    }

    /**
     * The 'drop-down' menu button next to an AutoCompleteTextView field.
     * Allows us to show a list of strings to choose from.
     * <p>
     * Note that a {@link ValuePicker} uses a plain AlertDialog.
     *
     * @param field         {@link Field} to edit
     * @param fieldButton   field/button to bind the PickListener to
     * @param fieldView     view to connect
     * @param dialogTitleId title of the dialog box.
     * @param list          list of strings to choose from.
     */
    public static void create(@NonNull final AutoCompleteTextView fieldView,
                              @NonNull final View fieldButton,
                              @NonNull final Field<String> field,
                              @StringRes final int dialogTitleId,
                              @NonNull final List<String> list) {
        final Context context = fieldView.getContext();

        // only bother when it's in use
        if (!field.isUsed(context)) {
            return;
        }

        if (list.isEmpty()) {
            fieldButton.setEnabled(false);
            return;
        }
        // We got a list, set it up.

        // Get the list to use in the AutoCompleteTextView
        DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                context, R.layout.dropdown_menu_popup_item, list);

        fieldView.setAdapter(adapter);

        // Get the drop-down button for the list and setup dialog
        fieldButton.setOnClickListener(v -> {
            FieldPicker<String> picker = new FieldPicker<>(context,
                                                           context.getString(dialogTitleId),
                                                           field, list);
            picker.show();
        });
    }

    private static class FieldListAdapter<T>
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final List<T> mItems;

        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final Field<T> mField;
        @NonNull
        private final PickListener<T> mListener;
        private int mPreSelectedPosition = -1;

        /**
         * Constructor.
         *
         * @param context  Current context
         * @param field    the Field
         * @param items    List of items
         * @param listener where to send the result back to
         */
        FieldListAdapter(@NonNull final Context context,
                         @NonNull final Field<T> field,
                         @NonNull final List<T> items,
                         @NonNull final PickListener<T> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
            mItems = items;

            mField = field;

            int position = 0;

            for (T listEntry : items) {
                if (listEntry.equals(field.getAccessor().getValue())) {
                    mPreSelectedPosition = position;
                    break;
                }
                position++;
            }
        }

        /**
         * @return the position of the original value, or {@code -1} if none.
         */
        int getPreSelectedPosition() {
            return mPreSelectedPosition;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View root = mInflater.inflate(R.layout.row_simple_list_item, parent, false);
            return new Holder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            T item = mItems.get(position);
            mField.getAccessor().setValue(item);

            // onClick on the whole view.
            holder.itemView.setOnClickListener(v -> mListener.onPicked(item));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView textView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.menu_item);
        }
    }
}
