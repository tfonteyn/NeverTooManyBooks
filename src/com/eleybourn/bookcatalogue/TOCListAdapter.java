package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapterRowActionListener;
import com.eleybourn.bookcatalogue.entities.TOCEntry;

import java.util.ArrayList;

/**
 * code sharing between edit and showing anthology titles, editing extends this class
 */
public class TOCListAdapter extends SimpleListAdapter<TOCEntry> implements SimpleListAdapterRowActionListener<TOCEntry> {

    @NonNull
    private final Context mContext;

    public TOCListAdapter(final @NonNull Context context,
                          final @LayoutRes int rowViewId,
                          final @NonNull ArrayList<TOCEntry> items) {
        super(context, rowViewId, items);
        mContext = context;
    }

    @Override
    public void onGetView(final @NonNull View convertView,
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
}
