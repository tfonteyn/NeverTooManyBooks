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
public class TocAdapter
        extends ArrayAdapter<TocEntry> {

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
    public TocAdapter(@NonNull final Context context,
                      @LayoutRes final int rowLayoutId,
                      @NonNull final List<TocEntry> objects) {
        super(context, rowLayoutId, objects);
        mInflater = LayoutInflater.from(context);
        mRowLayoutId = rowLayoutId;
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {
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
        holder.bind(getItem(position));

        return convertView;
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        @NonNull
        private final TextView titleView;
        @Nullable
        private final TextView authorView;
        @Nullable
        private final TextView firstPublicationView;

        public Holder(@NonNull final View rowView) {
            titleView = rowView.findViewById(R.id.title);
            // optional
            authorView = rowView.findViewById(R.id.author);
            // optional
            firstPublicationView = rowView.findViewById(R.id.year);

            // hook us up.
            rowView.setTag(this);
        }

        void bind(@NonNull final TocEntry item) {
            titleView.setText(item.getTitle());
            // optional
            if (authorView != null) {
                authorView.setText(item.getAuthor().getDisplayName());
            }
            // optional
            if (firstPublicationView != null) {
                String year = item.getFirstPublication();
                if (year.isEmpty()) {
                    firstPublicationView.setVisibility(View.GONE);
                } else {
                    firstPublicationView.setVisibility(View.VISIBLE);
                    firstPublicationView.setText(
                            firstPublicationView.getContext().getString(R.string.brackets, year));
                }
            }
        }
    }

}
