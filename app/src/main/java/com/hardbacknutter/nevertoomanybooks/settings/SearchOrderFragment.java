/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditSearchOrderBinding;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Handles the order of sites to search, and the individual site being enabled or not.
 * <p>
 * Persistence is handled in {@link SearchAdminActivity} / {@link SearchAdminViewModel}.
 */
public class SearchOrderFragment
        extends Fragment {

    /** Log tag. */
    private static final String TAG = "SearchOrderFragment";
    static final String BKEY_TYPE = TAG + ":type";

    private SearchSiteListAdapter mListAdapter;
    private ItemTouchHelper mItemTouchHelper;

    /* The View model. */
    @SuppressWarnings("FieldCanBeLocal")
    private SearchAdminViewModel mModel;

    /** View Binding. */
    private FragmentEditSearchOrderBinding mVb;

    /** The type of list we're handling in this fragment (tab). */
    private Site.Type mType;

    /**
     * The list we're handling in this fragment (tab).
     * Single-list mode: the list as passed in.
     * All-list mode: a local <strong>deep-copy</strong> of the {@link #mType} list.
     */
    private ArrayList<Site> mSiteList;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
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
        mModel = new ViewModelProvider(getActivity()).get(SearchAdminViewModel.class);
        mType = Objects.requireNonNull(requireArguments().getParcelable(BKEY_TYPE), "BKEY_TYPE");
        mSiteList = mModel.getList(mType);

        //noinspection ConstantConditions
        mVb.siteList.addItemDecoration(
                new DividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.siteList.setHasFixedSize(true);

        mListAdapter = new SearchSiteListAdapter(getContext(), mSiteList,
                                                 vh -> mItemTouchHelper.startDrag(vh));
        mVb.siteList.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.siteList);
    }

    @Override
    public void onResume() {
        super.onResume();
        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        actionBar.setTitle(R.string.lbl_settings);
        actionBar.setSubtitle(R.string.lbl_websites);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.action_reset_to_default)
            .setIcon(R.drawable.ic_undo)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_RESET) {
            final Locale systemLocale = AppLocale.getInstance().getSystemLocale();
            //noinspection ConstantConditions
            final Locale userLocale = AppLocale.getInstance().getUserLocale(getContext());

            // Reset the global/original list for the type.
            mType.resetList(getContext(), systemLocale, userLocale);
            // and replace the content of the local list with the (new) defaults.
            mSiteList.clear();
            mSiteList.addAll(mType.getSites());

            mListAdapter.notifyDataSetChanged();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static class SearchSiteListAdapter
            extends RecyclerViewAdapterBase<Site, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param sites             to use
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        SearchSiteListAdapter(@NonNull final Context context,
                              @NonNull final List<Site> sites,
                              @NonNull final StartDragListener dragStartListener) {
            super(context, sites, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_searchsite, parent, false);
            final Holder holder = new Holder(view);
            //noinspection ConstantConditions
            holder.mCheckableButton.setOnClickListener(v -> onItemCheckChanged(holder));
            return holder;
        }

        void onItemCheckChanged(@NonNull final Holder holder) {
            final Site site = getItem(holder.getBindingAdapterPosition());
            site.setEnabled(!site.isEnabled());
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(site.isEnabled());
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            @NonNull
            final Context context = getContext();

            final Site site = getItem(position);
            final SearchEngine searchEngine = site.getSearchEngine(context);

            holder.nameView.setText(searchEngine.getName(context));

            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(site.isEnabled());

            // only show the info for Data lists. Irrelevant for others.
            if (site.getType() == Site.Type.Data) {
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

                holder.infoView.setVisibility(View.VISIBLE);
            } else {
                holder.infoView.setVisibility(View.GONE);
            }
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

        Holder(@NonNull final View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.name);
            infoView = itemView.findViewById(R.id.info);
        }
    }
}
