/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.searchsites;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.tabs.TabLayout;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditSearchOrderBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditSearchsiteBinding;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;

/**
 * Handles the order of sites to search, and the individual site being enabled or not.
 * <p>
 * Persistence is handled in {@link SearchAdminFragment} / {@link SearchAdminViewModel}.
 */
public class SearchOrderFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "SearchOrderFragment";
    private static final String BKEY_TYPE = TAG + ":type";

    private static final String RK_MENU = TAG + ":rk:menu";

    private SearchSiteListAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    /* The View model. */
    private SearchAdminViewModel vm;

    /** View Binding. */
    private FragmentEditSearchOrderBinding vb;
    @Nullable
    private TabLayout tabPanel;

    private ExtMenuLauncher menuLauncher;

    /** The type of list we're handling in this fragment (tab). */
    private Site.Type type;

    /**
     * Constructor.
     *
     * @param type of the list to edit
     *
     * @return instance
     */
    @NonNull
    public static SearchOrderFragment create(@NonNull final Site.Type type) {
        final SearchOrderFragment fragment = new SearchOrderFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(BKEY_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        type = Objects.requireNonNull(requireArguments().getParcelable(BKEY_TYPE), BKEY_TYPE);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SearchAdminViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditSearchOrderBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Insets are applied to the parent fragment (ViewPager2)

        //noinspection DataFlowIssue
        tabPanel = getActivity().findViewById(R.id.tab_panel);

        //noinspection DataFlowIssue
        vb.siteList.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        vb.siteList.setHasFixedSize(true);

        adapter = new SearchSiteListAdapter(type, vm.getList(type),
                                                vh -> itemTouchHelper.startDrag(vh));
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(), (anchor, position) -> {
                    final Context context = anchor.getContext();
                    final Menu menu = MenuUtils.create(context);
                    menu.add(Menu.NONE, R.id.MENU_SETTINGS, 0, R.string.lbl_settings)
                        .setIcon(R.drawable.settings_24px);

                    final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
                    if (menuMode.isPopup()) {
                        new ExtMenuPopupWindow(context)
                                .setListener(this::onMenuItemSelected)
                                .setMenuOwner(position)
                                .setMenu(menu, true)
                                .show(anchor, menuMode);
                    } else {
                        menuLauncher.launch(getActivity(), null, null, position, menu, true);
                    }
                });

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);

        vb.siteList.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.siteList);

        //noinspection DataFlowIssue
        vm.onSiteListUpdated().observe(getActivity(), updatedType -> {
            // is it ours?
            if (updatedType == type) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param position   in the list
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     *
     * @throws IllegalStateException (debug) if the PreferenceFragment fails to instantiate
     */
    @SuppressLint("Range")
    private boolean onMenuItemSelected(@IntRange(from = RecyclerView.NO_POSITION) final int position,
                                       @IdRes final int menuItemId) {

        // should never be the case.... flw
        if (position == RecyclerView.NO_POSITION) {
            return false;
        }

        if (menuItemId == R.id.MENU_SETTINGS) {
            final EngineId engineId = vm.getList(type).get(position).getEngineId();
            final Class<? extends Fragment> pfc = engineId.getPreferenceFragmentClazz();
            // sanity check
            if (pfc == null) {
                return false;
            }

            if (tabPanel != null) {
                tabPanel.setVisibility(View.GONE);
            }

            final Fragment fragment;
            try {
                fragment = pfc.getConstructor().newInstance();
            } catch (@NonNull final IllegalAccessException
                                    | NoSuchMethodException
                                    | InstantiationException
                                    | InvocationTargetException
                                    | java.lang.InstantiationException e) {
                throw new IllegalStateException(e);
            }

            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack(engineId.name())
                    .replace(R.id.content_frame, fragment, engineId.name())
                    .commit();
            return true;
        }

        return false;
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends CheckableDragDropViewHolder {

        @NonNull
        private final RowEditSearchsiteBinding vb;

        Holder(@NonNull final RowEditSearchsiteBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        void onBind(@NonNull final Site site) {
            final Context context = itemView.getContext();
            vb.websiteName.setText(site.getEngineId().getName(context));

            setChecked(site.isActive());

            // only show the info for Data lists. Irrelevant for others.
            if (site.getType() == Site.Type.Data) {
                final EngineId engineId = site.getEngineId();
                // do not list SearchEngine.CoverByEdition, it's irrelevant to the user.
                final Collection<String> capabilities = new ArrayList<>();
                if (engineId.supports(SearchEngine.SearchBy.Isbn)) {
                    capabilities.add(context.getString(R.string.lbl_isbn));
                }
                if (engineId.supports(SearchEngine.SearchBy.Barcode)) {
                    capabilities.add(context.getString(R.string.lbl_barcode));
                }
                if (engineId.supports(SearchEngine.SearchBy.ExternalId)) {
                    capabilities.add(context.getString(R.string.lbl_tab_lbl_ext_id));
                }
                if (engineId.supports(SearchEngine.SearchBy.Text)) {
                    capabilities.add(context.getString(R.string.action_search));
                }
                vb.capabilities.setText(context.getString(R.string.brackets,
                                                          String.join(", ", capabilities)));
                vb.capabilities.setVisibility(View.VISIBLE);

                vb.info.setText(site.getEngineId().getInfo(context));
                vb.info.setVisibility(View.VISIBLE);
            } else {
                vb.capabilities.setVisibility(View.GONE);
                vb.info.setVisibility(View.GONE);
            }
        }
    }

    private static class SearchSiteListAdapter
            extends BaseDragDropRecyclerViewAdapter<Site, Holder> {

        @NonNull
        private final Site.Type type;

        /**
         * Constructor.
         *
         * @param type              of the list
         * @param sites             to use
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        SearchSiteListAdapter(@NonNull final Site.Type type,
                              @NonNull final List<Site> sites,
                              @NonNull final StartDragListener dragStartListener) {
            super(sites, dragStartListener);
            this.type = type;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditSearchsiteBinding vb = RowEditSearchsiteBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            final Holder holder = new Holder(vb);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnItemCheckChangedListener(position -> {
                final Site site = getItem(position);
                site.setActive(!site.isActive());
                notifyItemChanged(position);
                return site.isActive();
            });

            if (type == Site.Type.Data) {
                vb.ROWMENUBTN.setVisibility(View.VISIBLE);
                holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            } else {
                vb.ROWMENUBTN.setVisibility(View.GONE);
            }

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);
            holder.onBind(getItem(position));
        }
    }
}
