package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.entities.TOCEntry;

import java.util.ArrayList;

/**
 * code sharing between edit and showing anthology titles, editing extends this class
 */
public class TOCListAdapter extends SimpleListAdapter<TOCEntry> {

    @NonNull
    private final Context mContext;

    public TOCListAdapter(final @NonNull Context context,
                          @LayoutRes final int rowViewId,
                          final @NonNull ArrayList<TOCEntry> items) {
        super(context, rowViewId, items);
        mContext = context;
    }

    @Override
    protected void onSetupView(final @NonNull View convertView,
                               final @NonNull TOCEntry item) {

        TextView vTitle = convertView.findViewById(R.id.title);
        vTitle.setText(item.getTitle());

        // optional
        TextView vAuthor = convertView.findViewById(R.id.author);
        if (vAuthor != null) {
            vAuthor.setText(item.getAuthor().getDisplayName());
        }

        // optional
        TextView vYear = convertView.findViewById(R.id.year);
        if (vYear != null) {
            String year = item.getFirstPublication();
            if (year.isEmpty()) {
                vYear.setVisibility(View.GONE);
            } else {
                vYear.setVisibility(View.VISIBLE);
                vYear.setText(mContext.getString(R.string.brackets, item.getFirstPublication()));
            }
        }
    }

    /**
     * Called when an otherwise inactive part of the row is clicked.
     *
     * @param target The view clicked
     * @param item   The object associated with this row
     */
    @Override
    protected void onRowClick(final @NonNull View target, final @NonNull TOCEntry item, final int position) {
        //TODO: navigate to new Activity where we show the Anthology title + a list of all books it appears in
    }

    public class Holder {

    }
}
