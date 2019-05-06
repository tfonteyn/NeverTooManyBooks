package com.eleybourn.bookcatalogue.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.Site;
import com.eleybourn.bookcatalogue.widgets.SimpleAdapterDataObserver;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewViewHolderBase;
import com.eleybourn.bookcatalogue.widgets.ddsupport.OnStartDragListener;
import com.eleybourn.bookcatalogue.widgets.ddsupport.SimpleItemTouchHelperCallback;

/**
 * Ideally should use {@link EditObjectListActivity} but that needs to be converted
 * to a Fragment first.
 */
public class SearchOrderFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = SearchOrderFragment.class.getSimpleName();

    private ArrayList<Site> mList;
    @SuppressWarnings("FieldCanBeLocal")
    private SearchSiteListAdapter mListAdapter;
    private RecyclerView mListView;
    private ItemTouchHelper mItemTouchHelper;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_search_order, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle args = requireArguments();

        mList = args.getParcelableArrayList(SearchSites.BKEY_SEARCH_SITES);

        //noinspection ConstantConditions
        mListView = getView().findViewById(android.R.id.list);
        mListView.setLayoutManager(new LinearLayoutManager(getContext()));
        mListView.setHasFixedSize(true);

        //noinspection ConstantConditions
        mListAdapter = new SearchSiteListAdapter(
                getContext(), mList, (viewHolder) -> mItemTouchHelper.startDrag(viewHolder));
        // any change done in the adapter will set the book 'dirty'
        // if changing the list externally, make sure to always notify the adapter.
        mListAdapter.registerAdapterDataObserver(new SimpleAdapterDataObserver() {
            @Override
            public void onChanged() {
                //noinspection ConstantConditions
                ((SearchAdminActivity)getActivity()).setDirty(true);
            }
        });
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);
    }


    /**
     * @return the list, or {@code null} if this fragment was not displayed,
     * i.e. the list was not even loaded.
     */
    @Nullable
    public ArrayList<Site> getList() {
        // have we been brought to the front ?
        if (mListView != null) {
            // walk the list, and use the position of the item as the site.priority
            for (int row = 0; row < mList.size(); row++) {
                mList.get(row).setPriority(row);
            }
        }
        return mList;
    }

    private static class SearchSiteListAdapter
            extends RecyclerViewAdapterBase<Site, Holder> {

        /**
         * Constructor.
         *
         * @param context caller context
         * @param items   list of sites
         */
        SearchSiteListAdapter(@NonNull final Context context,
                              @NonNull final List<Site> items,
                              @NonNull final OnStartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_searchsite, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            Site site = getItem(position);

            holder.nameView.setText(site.getName());
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(site.isEnabled());

            // Set the click listener for the 'enable' site checkable
            holder.mCheckableButton.setOnClickListener(v -> {
                site.setEnabled(!site.isEnabled());
                holder.mCheckableButton.setChecked(site.isEnabled());
            });

        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView nameView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.name);
        }
    }
}
