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
import com.eleybourn.bookcatalogue.entities.TocEntry;

/**
 * A read-only adapter for viewing TocEntry's.
 */
public class TOCAdapter
        extends ArrayAdapter<TocEntry> {

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
    public TOCAdapter(@NonNull final Context context,
                      @LayoutRes final int rowLayoutId,
                      @NonNull final List<TocEntry> objects) {
        super(context, rowLayoutId, objects);
        mRowLayoutId = rowLayoutId;
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {
        final TocEntry item = getItem(position);

        Holder holder;
        if (convertView != null) {
            // Recycling: just get the holder
            holder = (Holder) convertView.getTag();
        } else {
            // Not recycling, get a new View and make the holder for it.
            convertView = LayoutInflater.from(getContext())
                                        .inflate(mRowLayoutId, parent,false);
            holder = new Holder(convertView);
        }

        //noinspection ConstantConditions
        holder.titleView.setText(item.getTitle());
        // optional
        if (holder.authorView != null) {
            holder.authorView.setText(item.getAuthor().getDisplayName());
        }
        // optional
        if (holder.firstPublicationView != null) {
            String year = item.getFirstPublication();
            if (year.isEmpty()) {
                holder.firstPublicationView.setVisibility(View.GONE);
            } else {
                holder.firstPublicationView.setVisibility(View.VISIBLE);
                holder.firstPublicationView.setText(
                        getContext().getString(R.string.brackets, item.getFirstPublication()));
            }
        }

        return convertView;
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        @NonNull
        final TextView titleView;
        @Nullable
        final TextView authorView;
        @Nullable
        final TextView firstPublicationView;

        public Holder(@NonNull final View rowView) {
            titleView = rowView.findViewById(R.id.title);
            // optional
            authorView = rowView.findViewById(R.id.author);
            // optional
            firstPublicationView = rowView.findViewById(R.id.year);

            rowView.setTag(this);
        }
    }

}
