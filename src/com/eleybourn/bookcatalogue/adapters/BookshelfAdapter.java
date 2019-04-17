package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

/**
 * Adapter and row Holder for a {@link Bookshelf}.
 * <p>
 * Displays the name in a TextView.
 */
public class BookshelfAdapter
        extends ArrayAdapter<Bookshelf> {

    @NonNull
    private final LayoutInflater mInflater;

    @LayoutRes
    private final int mRowLayoutId;

    /**
     * Constructor.
     *
     * @param context     the caller context
     * @param rowLayoutId The resource ID for a layout file containing a TextView to use when
     *                    instantiating views.
     * @param objects     The objects to represent in the ListView.
     */
    public BookshelfAdapter(@NonNull final Context context,
                            @LayoutRes final int rowLayoutId,
                            @NonNull final List<Bookshelf> objects) {
        super(context, rowLayoutId, objects);

        mInflater = LayoutInflater.from(context);
        mRowLayoutId = rowLayoutId;
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {

        final Bookshelf item = getItem(position);

        Holder holder;
        if (convertView != null) {
            // Recycling: just get the holder
            holder = (Holder) convertView.getTag();
        } else {
            // Not recycling, get a new View and make the holder for it.
            convertView = mInflater.inflate(mRowLayoutId, parent, false);
            holder = new Holder(convertView);
        }

        //noinspection ConstantConditions
        holder.nameView.setText(item.getName());

        return convertView;
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        @NonNull
        final TextView nameView;

        public Holder(@NonNull final View rowView) {
            nameView = rowView.findViewById(R.id.name);
            rowView.setTag(this);
        }
    }
}
