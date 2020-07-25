/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditSearchOrderBinding;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Handles the order of sites to search, and the individual site being enabled or not.
 * <p>
 * Persistence is handled in {@link SearchAdminModel}.
 */
public class SearchOrderFragment
        extends Fragment {

    private SearchSiteListAdapter mListAdapter;
    private ItemTouchHelper mItemTouchHelper;

    /** The list we're handling in this fragment (tab). */
    private SiteList.Type mOurType;

    private SearchAdminModel mModel;

    /** View binding. */
    private FragmentEditSearchOrderBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mOurType = requireArguments().getParcelable(SearchAdminModel.BKEY_LIST_TYPE);
        Objects.requireNonNull(mOurType);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditSearchOrderBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(SearchAdminModel.class);

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mVb.siteList.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mVb.siteList.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        mVb.siteList.setHasFixedSize(true);

        final List<Site> list = mModel.getList(getContext(), mOurType).getSites();
        mListAdapter = new SearchSiteListAdapter(getContext(), mOurType, list,
                                                 vh -> mItemTouchHelper.startDrag(vh));
        mVb.siteList.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.siteList);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.btn_reset)
            .setIcon(R.drawable.ic_undo)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_RESET: {
                final Locale systemLocale = LocaleUtils.getSystemLocale();
                //noinspection ConstantConditions
                final Locale userLocale = LocaleUtils.getUserLocale(getContext());
                mModel.resetList(getContext(), systemLocale, userLocale, mOurType);
                mListAdapter.notifyDataSetChanged();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private static class SearchSiteListAdapter
            extends RecyclerViewAdapterBase<Site, Holder> {

        private final boolean mShowInfo;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of sites
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        SearchSiteListAdapter(@NonNull final Context context,
                              @NonNull final SiteList.Type type,
                              @NonNull final List<Site> items,
                              @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);

            // only show the info for Data lists. Irrelevant for others.
            mShowInfo = type == SiteList.Type.Data;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_searchsite, parent, false);
            return new Holder(view, mShowInfo);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            @NonNull
            final Context context = getContext();

            final Site site = getItem(position);
            final SearchEngine searchEngine = site.getSearchEngine();

            holder.nameView.setText(searchEngine.getName());

            if (mShowInfo) {
                // do not list SearchEngine.CoverByIsbn, it's irrelevant to the user.
                final Collection<String> info = new ArrayList<>();
                if (searchEngine instanceof SearchEngine.ByIsbn) {
                    info.add(context.getString(R.string.lbl_isbn));
                }
                if (searchEngine instanceof SearchEngine.ByBarcode) {
                    info.add(context.getString(R.string.lbl_barcode));
                }
                if (searchEngine instanceof SearchEngine.ByExternalId) {
                    info.add(context.getString(R.string.tab_lbl_ext_id));
                }
                if (searchEngine instanceof SearchEngine.ByText) {
                    info.add(context.getString(android.R.string.search_go));
                }
                holder.infoView.setText(context.getString(R.string.brackets,
                                                          TextUtils.join(", ", info)));
            }

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
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView nameView;
        @NonNull
        final TextView infoView;

        Holder(@NonNull final View itemView,
               final boolean showInfo) {
            super(itemView);
            nameView = itemView.findViewById(R.id.name);
            infoView = itemView.findViewById(R.id.info);
            infoView.setVisibility(showInfo ? View.VISIBLE : View.GONE);
        }
    }
}
