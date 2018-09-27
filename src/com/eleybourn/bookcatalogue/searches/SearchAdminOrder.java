package com.eleybourn.bookcatalogue.searches;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueListActivity;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Should use {@link com.eleybourn.bookcatalogue.Fields.Field}
 * but:
 * - I don't yet grok it fully
 * - android has come with a 'standard' data-binding
 * - Field does not support CheckedTextView yet anyhow
 */
public class SearchAdminOrder extends BookCatalogueListActivity {

    private List<SearchManager.SearchSite> mList;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_search_order;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.search_internet);
        mList = SearchManager.getSiteSearchOrder();
        final SearchSiteListAdapter adapter = new SearchSiteListAdapter(this, R.layout.row_edit_searchsite, mList);
        setListAdapter(adapter);

        Button confirmBtn = findViewById(R.id.confirm);
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                List<SearchManager.SearchSite> newList = new ArrayList<>(mList);
                ListView list = getListView();
                for (int i=0; i < list.getChildCount(); i++) {
                    // get the current position of each site, and store that back into the site object.
                    View child = list.getChildAt(i);
                    int pos = adapter.getViewRow(child);
                    SearchManager.SearchSite site = adapter.getItem(pos);

                    //noinspection ConstantConditions
                    site.order = i;
                    newList.set(site.order, site);
                }
                SearchManager.setSearchOrder(newList);
                finish();
            }
        });

        Button cancelBtn = findViewById(R.id.cancel);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                finish();
            }
        });
    }

    private class SearchSiteListAdapter extends SimpleListAdapter<SearchManager.SearchSite> {

        SearchSiteListAdapter(@NonNull final Context context,
                              final int rowViewId,
                              @NonNull final List<SearchManager.SearchSite> list) {
            super(context, rowViewId, list);
        }

        @Override
        protected void onSetupView(@NonNull final View convertView, @NonNull final SearchManager.SearchSite item) {
            final TextView name = convertView.findViewById(R.id.row_name);
            name.setText(item.name);

            final CheckedTextView enabled = convertView.findViewById(R.id.row_enabled);
            enabled.setChecked(item.enabled);
            enabled.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    enabled.setChecked(!enabled.isChecked());
                    item.enabled = enabled.isChecked();
                }
            });
        }
    }
}