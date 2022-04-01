/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.databinding.RowAuthorWorkBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;

/**
 * Display all {@link TocEntry}'s for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 *
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

    private ToolbarMenuProvider mToolbarMenuProvider;
    /** The Fragment ViewModel. */
    private AuthorWorksViewModel mVm;
    /** Display a Book. */
    private final ActivityResultLauncher<ShowBookPagerContract.Input> mDisplayBookLauncher =
            registerForActivityResult(new ShowBookPagerContract(), this::onBookEditFinished);
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final Intent resultIntent = EditBookOutput
                            .createResultIntent(0, mVm.isDataModified());
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };
    /** React to changes in the adapter. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mToolbarMenuProvider.onPrepareMenu(getToolbar().getMenu());
                }
            };

    /** The Adapter. */
    private TocAdapter mListAdapter;
    /** View Binding. */
    private RecyclerView mWorksListView;
    private ExtPopupMenu mContextMenu;

    private void onBookEditFinished(@Nullable final EditBookOutput data) {
        // ignore the data.bookId
        if (data != null && data.modified) {
            mVm.setDataModified();
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(AuthorWorksViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_author_works, container, false);
        mWorksListView = view.findViewById(R.id.author_works);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());
        //noinspection ConstantConditions
        toolbar.setTitle(mVm.getScreenTitle(context));
        toolbar.setSubtitle(mVm.getScreenSubtitle(context));

        // Popup the search widget when the user starts to type.
        //noinspection ConstantConditions
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mWorksListView.setHasFixedSize(true);
        mWorksListView.addItemDecoration(new DividerItemDecoration(context, RecyclerView.VERTICAL));

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        // Optional overlay
        final int overlayType = Prefs.getFastScrollerOverlayType(global);
        FastScroller.attach(mWorksListView, overlayType);

        mListAdapter = new TocAdapter(context, mVm.getList());
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mWorksListView.setAdapter(mListAdapter);

        mContextMenu = new ExtPopupMenu(context);
        final Menu menu = mContextMenu.getMenu();
        final Resources res = getResources();
        menu.add(Menu.NONE, R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.action_delete)
            .setIcon(R.drawable.ic_baseline_delete_24);

        if (savedInstanceState == null) {
            TipManager.getInstance().display(context, R.string.tip_authors_works, null);
        }
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

        final AuthorWork work = mVm.getList().get(position);

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
                            mVm.delete(getContext(), work);
                            mListAdapter.notifyItemRemoved(position);
                        });
                break;
            }
            case Book:
            case BookLight: {
                //noinspection ConstantConditions
                StandardDialogs.deleteBook(
                        getContext(), work.getLabel(getContext()),
                        Collections.singletonList(work.getPrimaryAuthor()), () -> {
                            mVm.delete(getContext(), work);
                            mListAdapter.notifyItemRemoved(position);
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
        final AuthorWork work = mVm.getList().get(position);

        switch (work.getWorkType()) {
            case TocEntry: {
                final TocEntry tocEntry = (TocEntry) work;
                final ArrayList<Long> bookIdList = mVm.getBookIds(tocEntry);
                if (bookIdList.size() == 1) {
                    mDisplayBookLauncher.launch(new ShowBookPagerContract.Input(
                            bookIdList.get(0), mVm.getStyle().getUuid(), null, 0));

                } else {
                    // multiple books, open the list as a NEW ACTIVITY
                    final Intent intent = new Intent(getContext(), BooksOnBookshelf.class)
                            .putExtra(Book.BKEY_BOOK_ID_LIST, ParcelUtils.wrap(bookIdList))
                            // Open the list expanded, as otherwise you end up with
                            // the author as a single line, and no books shown at all,
                            // which can be quite confusing to the user.
                            .putExtra(BooksOnBookshelfViewModel.BKEY_LIST_STATE,
                                      (Parcelable) RebuildBooklist.Expanded);

                    if (mVm.isAllBookshelves()) {
                        intent.putExtra(BooksOnBookshelfViewModel.BKEY_BOOKSHELF,
                                        Bookshelf.ALL_BOOKS);
                    }
                    startActivity(intent);
                }
                break;
            }
            case Book:
            case BookLight: {
                mDisplayBookLauncher.launch(new ShowBookPagerContract.Input(
                        work.getId(), mVm.getStyle().getUuid(), null, 0));

                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    /**
     * Row ViewHolder for {@link TocAdapter}.
     */
    private static class Holder
            extends TocBaseAdapter.TocHolder {

        @NonNull
        private final RowAuthorWorkBinding vb;

        Holder(@NonNull final RowAuthorWorkBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        @NonNull
        @Override
        public ImageButton getIconBtnView() {
            return vb.btnType;
        }

        @NonNull
        @Override
        public TextView getTitleView() {
            return vb.title;
        }

        @NonNull
        @Override
        public TextView getFirstPublicationView() {
            return vb.year;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.author_works, menu);

            onPrepareMenu(menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            // show if we got here with a specific bookshelf selected.
            // hide if the bookshelf was set to Bookshelf.ALL_BOOKS.
            menu.findItem(R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES)
                .setVisible(mVm.getBookshelfId() != Bookshelf.ALL_BOOKS)
                .setChecked(mVm.isAllBookshelves());
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_AUTHOR_WORKS_ALL) {
                menuItem.setChecked(true);
                mVm.reloadWorkList(true, true);
                mListAdapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.MENU_AUTHOR_WORKS_TOC) {
                menuItem.setChecked(true);
                mVm.reloadWorkList(true, false);
                mListAdapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.MENU_AUTHOR_WORKS_BOOKS) {
                menuItem.setChecked(true);
                mVm.reloadWorkList(false, true);
                mListAdapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES) {
                final boolean checked = !menuItem.isChecked();
                menuItem.setChecked(checked);
                mVm.setAllBookshelves(checked);
                mVm.reloadWorkList();
                mListAdapter.notifyDataSetChanged();

                final Toolbar toolbar = getToolbar();
                //noinspection ConstantConditions
                toolbar.setTitle(mVm.getScreenTitle(getContext()));
                toolbar.setSubtitle(mVm.getScreenSubtitle(getContext()));
                return true;
            }

            return false;
        }
    }

    public class TocAdapter
            extends TocBaseAdapter {

        /**
         * Constructor.
         *
         * @param context Current context
         * @param tocList to show
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        TocAdapter(@NonNull final Context context,
                   @NonNull final List<AuthorWork> tocList) {
            super(context, tocList);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowAuthorWorkBinding vb = RowAuthorWorkBinding.inflate(mInflater, parent, false);
            final Holder holder = new Holder(vb);

            initTypeButton(holder.vb.btnType, viewType);

            // click -> get the book(s) for that entry and display.
            holder.itemView.setOnClickListener(v -> gotoBook(holder.getBindingAdapterPosition()));

            holder.itemView.setOnLongClickListener(v -> {
                mContextMenu.showAsDropDown(v, menuItem ->
                        onMenuItemSelected(menuItem, holder.getBindingAdapterPosition()));
                return true;
            });

            return holder;
        }
    }
}
