/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;

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

    /** The Fragment ViewModel. */
    private AuthorWorksViewModel vm;
    /** Display a Book. */
    private final ActivityResultLauncher<ShowBookPagerContract.Input> displayBookLauncher =
            registerForActivityResult(new ShowBookPagerContract(), o -> o.ifPresent(
                    data -> vm.onBookEditFinished(data)));

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, vm.createResultIntent());
                    getActivity().finish();
                }
            };

    /** The Adapter. */
    private TocAdapter adapter;
    /** View Binding. */
    private RecyclerView worksListView;
    private ExtPopupMenu contextMenu;
    private final TocEntryHandler tocEntryHandler = new TocEntryHandler() {
        @Override
        public void viewBook(final int position) {
            gotoBook(position);
        }

        @Override
        public void showContextMenu(@NonNull final View anchor,
                                    final int position) {
            contextMenu.showAsDropDown(anchor, menuItem ->
                    onMenuItemSelected(menuItem, position));
        }
    };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(AuthorWorksViewModel.class);
        //noinspection ConstantConditions
        vm.init(getContext(), requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_author_works, container, false);
        worksListView = view.findViewById(R.id.author_works);
        return view;
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

        final AuthorWork work = vm.getWorks().get(position);

        if (itemId == R.id.MENU_DELETE) {
            deleteWork(position, work);
            return true;
        }
        return false;
    }

    private void deleteWork(final int position,
                            @NonNull final AuthorWork work) {
        switch (work.getWorkType()) {
            case TocEntry: {
                //noinspection ConstantConditions
                StandardDialogs.deleteTocEntry(
                        getContext(), work.getLabel(getContext()),
                        work.getPrimaryAuthor(), () -> {
                            vm.delete(getContext(), work);
                            adapter.notifyItemRemoved(position);
                        });
                break;
            }
            case Book:
            case BookLight: {
                //noinspection ConstantConditions
                StandardDialogs.deleteBook(
                        getContext(), work.getLabel(getContext()),
                        Collections.singletonList(work.getPrimaryAuthor()), () -> {
                            vm.delete(getContext(), work);
                            adapter.notifyItemRemoved(position);
                        });
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     *
     * @param position in the list
     */
    private void gotoBook(final int position) {
        final AuthorWork work = vm.getWorks().get(position);

        switch (work.getWorkType()) {
            case TocEntry: {
                final TocEntry tocEntry = (TocEntry) work;
                final ArrayList<Long> bookIdList = vm.getBookIds(tocEntry);
                if (bookIdList.size() == 1) {
                    displayBookLauncher.launch(new ShowBookPagerContract.Input(
                            bookIdList.get(0), vm.getStyle().getUuid(), null, 0));

                } else {
                    // multiple books, open the list as a NEW ACTIVITY
                    final Intent intent = new Intent(getContext(), BooksOnBookshelf.class)
                            .putExtra(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList))
                            // Open the list expanded, as otherwise you end up with
                            // the author as a single line, and no books shown at all,
                            // which can be quite confusing to the user.
                            .putExtra(BooksOnBookshelfViewModel.BKEY_LIST_STATE,
                                      (Parcelable) RebuildBooklist.Expanded);

                    if (vm.isAllBookshelves()) {
                        intent.putExtra(DBKey.FK_BOOKSHELF, Bookshelf.ALL_BOOKS);
                    }
                    startActivity(intent);
                }
                break;
            }
            case Book:
            case BookLight: {
                displayBookLauncher.launch(new ShowBookPagerContract.Input(
                        work.getId(), vm.getStyle().getUuid(), null, 0));

                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());
        //noinspection ConstantConditions
        toolbar.setTitle(vm.getScreenTitle(context));
        toolbar.setSubtitle(vm.getScreenSubtitle(context));

        // Popup the search widget when the user starts to type.
        //noinspection ConstantConditions
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        worksListView.setHasFixedSize(true);
        worksListView.addItemDecoration(
                new MaterialDividerItemDecoration(context, RecyclerView.VERTICAL));

        // Optional overlay
        final int overlayType = Prefs.getFastScrollerOverlayType(context);
        FastScroller.attach(worksListView, overlayType);

        adapter = new TocAdapter(context, vm.getAuthor(), vm.getWorks(), tocEntryHandler);
        worksListView.setAdapter(adapter);

        contextMenu = new ExtPopupMenu(context);
        final Menu menu = contextMenu.getMenu();
        final Resources res = getResources();
        menu.add(Menu.NONE, R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.action_delete)
            .setIcon(R.drawable.ic_baseline_delete_24);

        if (savedInstanceState == null) {
            TipManager.getInstance().display(context, R.string.tip_authors_works, null);
        }
    }

    public static class TocAdapter
            extends TocBaseAdapter {

        /**
         * Constructor.
         *
         * @param context         Current context
         * @param worksAuthor     the author who 'owns' the works list
         * @param tocList         to show
         * @param tocEntryHandler the handler to act on row clicks
         */
        TocAdapter(@NonNull final Context context,
                   @NonNull final Author worksAuthor,
                   @NonNull final List<AuthorWork> tocList,
                   @NonNull final TocEntryHandler tocEntryHandler) {
            super(context, worksAuthor, tocList, tocEntryHandler);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final Holder holder = super.onCreateViewHolder(parent, viewType);

            // click -> get the book(s) for that entry and display.
            holder.itemView.setOnClickListener(
                    v -> tocEntryHandler.viewBook(holder.getBindingAdapterPosition()));

            holder.itemView.setOnLongClickListener(v -> {
                tocEntryHandler.showContextMenu(v, holder.getBindingAdapterPosition());
                return true;
            });

            return holder;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.author_works, menu);

            //noinspection ConstantConditions
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
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_AUTHOR_WORKS_SORT_TITLE) {
                menuItem.setChecked(true);
                vm.setOrderByColumn(DBKey.TITLE_OB);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.MENU_AUTHOR_WORKS_SORT_FIRST_PUBLICATION_DATE) {
                menuItem.setChecked(true);
                vm.setOrderByColumn(DBKey.FIRST_PUBLICATION__DATE);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.MENU_AUTHOR_WORKS_FILTER_ALL) {
                menuItem.setChecked(true);
                vm.setFilter(true, true);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.MENU_AUTHOR_WORKS_FILTER_TOC) {
                menuItem.setChecked(true);
                vm.setFilter(true, false);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.MENU_AUTHOR_WORKS_FILTER_BOOKS) {
                menuItem.setChecked(true);
                vm.setFilter(false, true);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES) {
                final boolean checked = !menuItem.isChecked();
                menuItem.setChecked(checked);
                vm.setAllBookshelves(checked);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();

                final Toolbar toolbar = getToolbar();
                //noinspection ConstantConditions
                toolbar.setTitle(vm.getScreenTitle(getContext()));
                toolbar.setSubtitle(vm.getScreenSubtitle(getContext()));
                return true;
            }

            return false;
        }
    }
}
