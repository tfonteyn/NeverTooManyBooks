package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * code sharing between edit and showing anthology titles, editing extends this class
 */
public class TOCListAdapter extends SimpleListAdapter<TOCEntry> {

    protected TOCListAdapter(final @NonNull Context context,
                             final @LayoutRes int rowViewId,
                             final @NonNull ArrayList<TOCEntry> items) {
        super(context, rowViewId, items);
    }

    @Override
    public void onGetView(final @NonNull View convertView,
                          final @NonNull TOCEntry tocEntry) {

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
                holder.firstPublication.setText(getContext().getString(R.string.brackets, tocEntry.getFirstPublication()));
            }
        }
    }

    /**
     * Holder pattern for each row.
     */
    private class Holder {
        TextView title;
        TextView author;
        TextView firstPublication;
    }
}
