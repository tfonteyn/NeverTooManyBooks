package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.datamanager.Fields;

/**
 * @param <T> type of the actual Object that is represented by a row in the selection list.
 */
public class FieldPicker<T>
        extends ValuePicker {

    /**
     * Value picker for a text field.
     * <p>
     * TODO: use {@link com.eleybourn.bookcatalogue.entities.Entity} instead of a field value.
     * But not all (far from) the values are entities.
     *
     * @param context caller context
     * @param field   to get/set
     * @param list    list to choose from
     */
    public FieldPicker(@NonNull final Context context,
                       @Nullable final String title,
                       @NonNull final Fields.Field field,
                       @NonNull final List<T> list) {
        super(context, title, null);

        final FieldListAdapter<T> adapter =
                new FieldListAdapter<>(context, field, list, (item) -> {
                    dismiss();
                    field.setValue(item.toString());
                });
        setAdapter(adapter, adapter.getPreSelectedPosition());
    }

    private static class FieldListAdapter<T>
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final List<T> mItems;

        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final Fields.Field mField;
        @NonNull
        private final OnClickListener<T> mListener;
        private int mPreSelectedPosition = -1;

        FieldListAdapter(@NonNull final Context context,
                         @NonNull final Fields.Field field,
                         @NonNull final List<T> items,
                         @NonNull final OnClickListener<T> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
            mItems = items;

            mField = field;

            int position = 0;

            for (T listEntry : items) {
                if (listEntry.equals(field.getValue())) {
                    mPreSelectedPosition = position;
                    break;
                }
                position++;
            }
        }

        /**
         * @return the position of the original value, or -1 if none.
         */
        int getPreSelectedPosition() {
            return mPreSelectedPosition;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            View root = mInflater.inflate(R.layout.row_simple_dialog_list_item, parent, false);
            return new Holder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            T item = mItems.get(position);
            holder.textView.setText(mField.format(item.toString()));

            // onClick on the whole view.
            holder.itemView.setOnClickListener((v) -> mListener.onClick(item));
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
