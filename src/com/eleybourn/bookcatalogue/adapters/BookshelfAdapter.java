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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

import java.util.List;

/**
 * Adapter and row Holder for a {@link Bookshelf}.
 *
 * Displays the name in a TextView.
 */
public class BookshelfAdapter
        extends ArrayAdapter<Bookshelf> {

    @LayoutRes
    private final int mRowLayoutId;

    /**
     * Constructor.
     *
     * @param context     The current context.
     * @param rowLayoutId The resource ID for a layout file containing a TextView to use when
     *                    instantiating views.
     * @param objects     The objects to represent in the ListView.
     */
    public BookshelfAdapter(@NonNull final Context context,
                      @LayoutRes final int rowLayoutId,
                      @NonNull final List<Bookshelf> objects) {
        super(context, rowLayoutId, objects);
        mRowLayoutId = rowLayoutId;
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {

        final Bookshelf item = getItem(position);

        Holder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mRowLayoutId, null);
            holder = new Holder();
            holder.nameView = convertView.findViewById(R.id.name);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        //noinspection ConstantConditions
        holder.nameView.setText(item.getName());

        return convertView;
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {
        TextView nameView;
    }
}
