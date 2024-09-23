/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import java.util.Collections;
import java.util.List;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.DisplayBookLauncher;
import com.hardbacknutter.nevertoomanybooks.bookdetails.AuthorWorksAdapter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAuthorWorksBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;

/**
 * Display all {@link TocEntry}'s for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 * <p>
 * <strong>Note:</strong> when an item is clicked, we start a <strong>NEW</strong> Activity.
 * Doing a 'back' will then get the user back here.
 * This is intentionally different from the behaviour of {@link SearchFtsFragment}.
 */
public class AuthorWorksFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "AuthorWorksFragment";
    /** Optional. Show the TOC. Defaults to {@code true}. */
    static final String BKEY_WITH_TOC = TAG + ":tocs";
    /** Optional. Show the books. Defaults to {@code true}. */
    static final String BKEY_WITH_BOOKS = TAG + ":books";
    private static final String RK_MENU = TAG + ":rk:menu";
    /** The Fragment ViewModel. */
    private AuthorWorksViewModel vm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, vm.createResultIntent());
                    getActivity().finish();
                }
            };
    /** Display a Book. */
    private DisplayBookLauncher displayBookLauncher;
    private ExtMenuLauncher menuLauncher;
    /** The Adapter. */
    private AuthorWorksAdapter adapter;
    /** View Binding. */
    private FragmentAuthorWorksBinding vb;
    private Menu rowMenu;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(AuthorWorksViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), requireArguments());

        final FragmentManager fm = getChildFragmentManager();

        displayBookLauncher = new DisplayBookLauncher(this, o ->
                o.ifPresent(data -> vm.setDataModified(data)));

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentAuthorWorksBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(vb.authorWorks);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Context context = getContext();

        final Toolbar toolbar = getToolbar();
        //noinspection DataFlowIssue
        toolbar.setTitle(vm.getScreenTitle(context));
        toolbar.setSubtitle(vm.getScreenSubtitle(context));
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        vb.authorWorks.setHasFixedSize(true);

        // Optional overlay
        final int overlayType = Prefs.getFastScrollerOverlayType(context);
        FastScroller.attach(vb.authorWorks, overlayType);

        adapter = new AuthorWorksAdapter(context, vm.getStyle(), List.of(vm.getAuthor()),
                                         vm.getWorks());

        // click -> get the book(s) for that entry and display.
        adapter.setOnRowClickListener(
                (v, position) -> displayBookLauncher.launch(
                        this,
                        vm.getWorks().get(position),
                        vm.getStyle(),
                        vm.isAllBookshelves()));

        final Resources res = getResources();
        rowMenu = MenuUtils.create(context);
        rowMenu.add(Menu.NONE, R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                    R.string.action_delete)
               .setIcon(R.drawable.delete_24px);

        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(context),
                (anchor, position) -> {
                    final MenuMode menuMode = MenuMode.getMode(getActivity(), rowMenu);
                    if (menuMode.isPopup()) {
                        new ExtMenuPopupWindow(anchor.getContext())
                                .setListener(this::onMenuItemSelected)
                                .setMenuOwner(position)
                                .setMenu(rowMenu, true)
                                .show(anchor, menuMode);
                    } else {
                        menuLauncher.launch(getActivity(), position, null, null,
                                            rowMenu, true);
                    }
                }
        );

        vb.authorWorks.setAdapter(adapter);

        if (savedInstanceState == null) {
            TipManager.getInstance().display(context, R.string.tip_authors_works, null);
        }
    }

    /**
     * Menu selection listener.
     *
     * @param position   in the list
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(final int position,
                                       @IdRes final int menuItemId) {

        final AuthorWork work = vm.getWorks().get(position);

        if (menuItemId == R.id.MENU_DELETE) {
            deleteWork(position, work);
            return true;
        }
        return false;
    }

    private void deleteWork(final int position,
                            @NonNull final AuthorWork work) {
        Author primaryAuthor = work.getPrimaryAuthor();
        // Sanity check
        if (primaryAuthor == null) {
            //noinspection DataFlowIssue
            primaryAuthor = Author.createUnknownAuthor(getContext());
        }
        switch (work.getWorkType()) {
            case TocEntry: {
                //noinspection DataFlowIssue
                StandardDialogs.deleteTocEntry(
                        getContext(),
                        work.getLabel(getContext(), Details.AutoSelect, vm.getStyle()),
                        primaryAuthor, () -> {
                            vm.delete(getContext(), work);
                            adapter.notifyItemRemoved(position);
                        });
                break;
            }
            case Book:
            case BookLight: {
                //noinspection DataFlowIssue
                StandardDialogs.deleteBook(
                        getContext(),
                        work.getLabel(getContext(), Details.AutoSelect, vm.getStyle()),
                        Collections.singletonList(primaryAuthor), () -> {
                            vm.delete(getContext(), work);
                            adapter.notifyItemRemoved(position);
                        });
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }



    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.author_works, menu);

            //noinspection DataFlowIssue
            MenuUtils.customizeMenuGroupTitle(getContext(), menu,
                                              R.id.sm_title_author_works_sort);
            MenuUtils.customizeMenuGroupTitle(getContext(), menu,
                                              R.id.sm_title_author_works_filter);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            // show if we got here with a specific bookshelf selected.
            // hide if the bookshelf was set to Bookshelf.ALL_BOOKS.
            menu.findItem(R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES)
                .setVisible(vm.getBookshelfId() != Bookshelf.ALL_BOOKS)
                .setChecked(vm.isAllBookshelves());
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_AUTHOR_WORKS_SORT_TITLE) {
                menuItem.setChecked(true);
                vm.setOrderByColumn(DBKey.TITLE_OB);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_SORT_FIRST_PUBLICATION_DATE) {
                menuItem.setChecked(true);
                vm.setOrderByColumn(DBKey.FIRST_PUBLICATION__DATE);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_FILTER_ALL) {
                menuItem.setChecked(true);
                vm.setFilter(true, true);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_FILTER_TOC) {
                menuItem.setChecked(true);
                vm.setFilter(true, false);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_FILTER_BOOKS) {
                menuItem.setChecked(true);
                vm.setFilter(false, true);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES) {
                final boolean checked = !menuItem.isChecked();
                menuItem.setChecked(checked);
                vm.setAllBookshelves(checked);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();

                final Toolbar toolbar = getToolbar();
                //noinspection DataFlowIssue
                toolbar.setTitle(vm.getScreenTitle(getContext()));
                toolbar.setSubtitle(vm.getScreenSubtitle(getContext()));
                return true;
            }

            return false;
        }
    }
}
