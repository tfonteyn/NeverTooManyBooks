package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;

import java.util.ArrayList;

/**
 * code sharing between edit and showing anthology titles, editing extends this class
 */
public class AnthologyTitleListAdapter extends SimpleListAdapter<AnthologyTitle> {

    @NonNull
    private final Context mContext;

    public AnthologyTitleListAdapter(@NonNull final Context context,
                                     @LayoutRes final int rowViewId,
                                     @NonNull final ArrayList<AnthologyTitle> items) {
        super(context, rowViewId, items);
        mContext = context;
    }

    @Override
    protected void onSetupView(@NonNull final View convertView,
                               @NonNull final AnthologyTitle item) {

        // always
        TextView vTitle = convertView.findViewById(R.id.title);
        vTitle.setText(item.getTitle());

        TextView vAuthor = convertView.findViewById(R.id.author);
        if (vAuthor != null) {
            vAuthor.setText(item.getAuthor().getDisplayName());
        }

        // maybe not there
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
    protected void onRowClick(@NonNull final View target, @NonNull final AnthologyTitle item, final int position) {
        //TODO: navigate to new Activity where we show the Anthology title + a list of all books it appears in
    }
}
