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
import com.eleybourn.bookcatalogue.entities.TocEntry;

import java.util.List;

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
     * @param context     The current context.
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
        final TocEntry item = this.getItem(position);

        Holder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mRowLayoutId, null);
            // New view, so build the Holder
            holder = new Holder();
            holder.titleView = convertView.findViewById(R.id.title);
            holder.authorView = convertView.findViewById(R.id.author);
            holder.firstPublicationView = convertView.findViewById(R.id.year);

            // make it flash
            convertView.setBackgroundResource(android.R.drawable.list_selector_background);

            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
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

        TextView titleView;
        TextView authorView;
        TextView firstPublicationView;
    }

}
