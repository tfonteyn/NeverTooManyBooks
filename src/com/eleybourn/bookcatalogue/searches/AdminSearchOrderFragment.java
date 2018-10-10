package com.eleybourn.bookcatalogue.searches;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;

import java.util.ArrayList;
import java.util.List;

public class AdminSearchOrderFragment extends Fragment {

    private ListView mListView;
    @Nullable
    private ArrayList<SearchManager.SearchSite> mList;
    @Nullable
    private SearchSiteListAdapter mAdapter;

    private boolean isCreated;

    @Override
    public void setArguments(@Nullable final Bundle args) {
        //noinspection ConstantConditions,unchecked
        mList = (ArrayList<SearchManager.SearchSite>)args.getSerializable(SearchManager.BKEY_SEARCH_SITES);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_search_order, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAdapter = new SearchSiteListAdapter(this.getContext(), R.layout.row_edit_searchsite, mList);
        mListView = getView().findViewById(android.R.id.list);
        mListView.setAdapter(mAdapter);
        isCreated = true;
    }

    @Nullable
    public ArrayList<SearchManager.SearchSite> getList() {
        if (isCreated) {
            ArrayList<SearchManager.SearchSite> newList = new ArrayList<>(mList);
            for (int i = 0; i < mListView.getChildCount(); i++) {
                // get the current position of each site, and store that back into the site object.
                View child = mListView.getChildAt(i);
                int pos = mAdapter.getViewRow(child);
                SearchManager.SearchSite site = mAdapter.getItem(pos);

                //noinspection ConstantConditions
                site.order = i;
                newList.set(site.order, site);
            }
            mList = newList;
        }
        return mList;
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
