package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * code sharing between edit and showing anthology titles, editing extends this class.
 */
public class TOCListAdapter
        extends SimpleListAdapter<TOCEntry> {

    /**
     * Constructor.
     *
     * @param items for the list view.
     */
    TOCListAdapter(@NonNull final Context context,
                   @LayoutRes final int rowViewId,
                   @NonNull final ArrayList<TOCEntry> items) {
        super(context, rowViewId, items);
    }

    @Override
    public void onGetView(@NonNull final View convertView,
                          @NonNull final TOCEntry tocEntry) {

        Holder holder = ViewTagger.getTag(convertView);
        if (holder == null) {
            // New view, so build the Holder
            holder = new Holder();
            holder.title = convertView.findViewById(R.id.title);
            holder.author = convertView.findViewById(R.id.author);
            holder.firstPublication = convertView.findViewById(R.id.year);
            // Tag the parts that need it
            ViewTagger.setTag(convertView, holder);
        }

        holder.title.setText(tocEntry.getTitle());
        // optional
        if (holder.author != null) {
            holder.author.setText(tocEntry.getAuthor().getDisplayName());
        }
        // optional
        if (holder.firstPublication != null) {
            String year = tocEntry.getFirstPublication();
            if (year.isEmpty()) {
                holder.firstPublication.setVisibility(View.GONE);
            } else {
                holder.firstPublication.setVisibility(View.VISIBLE);
                holder.firstPublication.setText(
                        getContext().getString(R.string.brackets, tocEntry.getFirstPublication()));
            }
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        TextView title;
        TextView author;
        TextView firstPublication;
    }
}
