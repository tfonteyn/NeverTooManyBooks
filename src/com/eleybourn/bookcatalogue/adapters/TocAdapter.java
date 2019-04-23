package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.entities.TocEntry;

/**
 * A read-only adapter for viewing TocEntry's.
 */
public class TocAdapter
        extends RecyclerView.Adapter<TocAdapter.Holder> {

    @NonNull
    private final LayoutInflater mInflater;

    @LayoutRes
    private final int mRowLayoutId;

    @NonNull
    private final List<TocEntry> mList;

    /**
     * Constructor.
     *
     * @param context caller context
     * @param rowLayoutId The resource ID for a layout file containing a TextView to use when
     *                    instantiating views.
     * @param objects     The objects to represent in the ListView.
     */
    public TocAdapter(@NonNull final Context context,
                      @LayoutRes final int rowLayoutId,
                      @NonNull final List<TocEntry> objects) {
        mInflater = LayoutInflater.from(context);
        mRowLayoutId = rowLayoutId;
        mList = objects;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        View view = mInflater.inflate(mRowLayoutId, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        holder.bind(mList.get(position));
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    /**
     * Holder pattern for each row.
     */
    public static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final TextView titleView;
        @Nullable
        private final TextView authorView;
        @Nullable
        private final TextView firstPublicationView;

        public Holder(@NonNull final View rowView) {
            super(rowView);

            titleView = rowView.findViewById(R.id.title);
            // optional
            authorView = rowView.findViewById(R.id.author);
            // optional
            firstPublicationView = rowView.findViewById(R.id.year);
        }

        void bind(@NonNull final TocEntry item) {
            titleView.setText(item.getTitle());
            // optional
            if (authorView != null) {
                authorView.setText(item.getAuthor().getLabel());
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
