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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AuthorWorksContract;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAuthorWorksBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowAuthorWorkBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookAsWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 *
 * <strong>Note:</strong> when an item is clicked, we start a <strong>NEW</strong> Activity.
 * Doing a 'back' will then get the user back here.
 * This is intentionally different from the behaviour of {@link SearchFtsFragment}.
 */
public class AuthorWorksFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "AuthorWorksFragment";

    /** Optional. Show the TOC. Defaults to {@code true}. */
    static final String BKEY_WITH_TOC = TAG + ":tocs";
    /** Optional. Show the books. Defaults to {@code true}. */
    static final String BKEY_WITH_BOOKS = TAG + ":books";

    /** The Fragment ViewModel. */
    private AuthorWorksViewModel mVm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    AuthorWorksContract.setResultAndFinish(getActivity(), mVm.isDataModified());
                }
            };
    /** The Adapter. */
    private TocAdapter mAdapter;

    /** View Binding. */
    private FragmentAuthorWorksBinding mVb;
    private ExtPopupMenu mContextMenu;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mVm = new ViewModelProvider(this).get(AuthorWorksViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentAuthorWorksBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Popup the search widget when the user starts to type.
        //noinspection ConstantConditions
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        final Context context = getContext();

        //noinspection ConstantConditions
        setTitle(mVm.getScreenTitle(context));
        setSubtitle(mVm.getScreenSubtitle());

        mVb.authorWorks.setHasFixedSize(true);
        mVb.authorWorks.addItemDecoration(
                new DividerItemDecoration(context, RecyclerView.VERTICAL));

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        // Optional overlay
        final int overlayType = Prefs.getFastScrollerOverlayType(global);
        FastScroller.attach(mVb.authorWorks, overlayType);

        mAdapter = new TocAdapter(context);
        mVb.authorWorks.setAdapter(mAdapter);

        final Resources res = getResources();
        final Menu menu = ExtPopupMenu.createMenu(context);
        menu.add(Menu.NONE, R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.action_delete)
            .setIcon(R.drawable.ic_baseline_delete_24);

        mContextMenu = new ExtPopupMenu(context, menu, this::onContextItemSelected);

        if (savedInstanceState == null) {
            TipManager.getInstance().display(context, R.string.tip_authors_works, null);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        MenuCompat.setGroupDividerEnabled(menu, true);
        inflater.inflate(R.menu.author_works, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        final MenuItem all = menu.findItem(R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES);
        // show if we got here with a specific bookshelf selected.
        // hide if the bookshelf was set to Bookshelf.ALL_BOOKS.
        all.setVisible(mVm.getBookshelfId() != Bookshelf.ALL_BOOKS);

        all.setChecked(mVm.isAllBookshelves());

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_AUTHOR_WORKS_ALL) {
            item.setChecked(true);
            mVm.reloadWorkList(true, true);
            mAdapter.notifyDataSetChanged();
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_WORKS_TOC) {
            item.setChecked(true);
            mVm.reloadWorkList(true, false);
            mAdapter.notifyDataSetChanged();
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_WORKS_BOOKS) {
            item.setChecked(true);
            mVm.reloadWorkList(false, true);
            mAdapter.notifyDataSetChanged();
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES) {
            final boolean checked = !item.isChecked();
            item.setChecked(checked);
            mVm.setAllBookshelves(checked);
            mVm.reloadWorkList();
            mAdapter.notifyDataSetChanged();
            //noinspection ConstantConditions
            setTitle(mVm.getScreenTitle(getContext()));
            setSubtitle(mVm.getScreenSubtitle());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          final int position) {
        final int itemId = menuItem.getItemId();

        final AuthorWork work = mVm.getWorks().get(position);

        if (itemId == R.id.MENU_DELETE) {
            deleteWork(position, work);
            return true;
        }
        return false;
    }

    private void deleteWork(final int position,
                            @NonNull final AuthorWork work) {
        if (work instanceof TocEntry) {
            //noinspection ConstantConditions
            StandardDialogs.deleteTocEntry(
                    getContext(), work.getLabel(getContext()),
                    work.getPrimaryAuthor(), () -> {
                        mVm.delete(getContext(), work);
                        mAdapter.notifyItemRemoved(position);
                    });

        } else if (work instanceof BookAsWork) {
            //noinspection ConstantConditions
            StandardDialogs.deleteBook(
                    getContext(), work.getLabel(getContext()),
                    Collections.singletonList(work.getPrimaryAuthor()), () -> {
                        mVm.delete(getContext(), work);
                        mAdapter.notifyItemRemoved(position);
                    });
        } else {
            throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     *
     * @param position in the list
     */
    private void gotoBook(final int position) {
        final AuthorWork work = mVm.getWorks().get(position);

        switch (work.getWorkType()) {
            case AuthorWork.TYPE_TOC: {
                final TocEntry tocEntry = (TocEntry) work;
                final ArrayList<Long> bookIdList = mVm.getBookIds(tocEntry);
                if (bookIdList.size() == 1) {
                    // open new activity to show the book, 'back' will return to this one.
                    //noinspection ConstantConditions
                    final Intent intent = ShowBookFragment
                            .createIntent(getContext(), bookIdList.get(0));
                    startActivity(intent);

                } else {
                    // multiple books, open the list as a NEW ACTIVITY
                    final Intent intent = new Intent(getContext(), BooksOnBookshelf.class)
                            .putExtra(Book.BKEY_BOOK_ID_LIST, bookIdList)
                            // Open the list expanded, as otherwise you end up with
                            // the author as a single line, and no books shown at all,
                            // which can be quite confusing to the user.
                            .putExtra(BooksOnBookshelfViewModel.BKEY_LIST_STATE,
                                      RebuildBooklist.EXPANDED);

                    if (mVm.isAllBookshelves()) {
                        intent.putExtra(BooksOnBookshelfViewModel.BKEY_BOOKSHELF,
                                        Bookshelf.ALL_BOOKS);
                    }
                    startActivity(intent);
                }
                break;
            }
            case AuthorWork.TYPE_BOOK: {
                // open new activity to show the book, 'back' will return to this one.
                //noinspection ConstantConditions
                final Intent intent = ShowBookFragment.createIntent(getContext(), work.getId());
                startActivity(intent);
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
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowAuthorWorkBinding vb;

        Holder(@NonNull final RowAuthorWorkBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }

    public class TocAdapter
            extends RecyclerView.Adapter<Holder>
            implements FastScroller.PopupTextProvider {

        /** Cached inflater. */
        private final LayoutInflater mInflater;

        private final String mBookStr;
        private final Drawable mBookEntryIcon;

        private final String mMultipleBooksStr;
        private final Drawable mTocEntryIcon;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        @SuppressLint("UseCompatLoadingForDrawables")
        TocAdapter(@NonNull final Context context) {
            mInflater = LayoutInflater.from(context);

            final Resources.Theme theme = context.getTheme();
            final Resources res = context.getResources();

            // The entry is an actual book
            mBookStr = res.getString(R.string.lbl_book);
            mBookEntryIcon = res.getDrawable(R.drawable.ic_baseline_book_24, theme);
            // The entry is a story (etc...) which appears in multiple books.
            mMultipleBooksStr = res.getString(R.string.tip_authors_works_multiple_books);
            mTocEntryIcon = res.getDrawable(R.drawable.ic_baseline_library_books_24, theme);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final RowAuthorWorkBinding vb = RowAuthorWorkBinding.inflate(mInflater, parent, false);

            final Holder holder = new Holder(vb);

            // click -> get the book(s) for that entry and display.
            holder.itemView.setOnClickListener(v -> gotoBook(holder.getBindingAdapterPosition()));

            holder.itemView.setOnLongClickListener(v -> {
                mContextMenu.showAsDropDown(v, holder.getBindingAdapterPosition());
                return true;
            });

            return holder;
        }

        @SuppressLint("UseCompatTextViewDrawableApis")
        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final Context context = mInflater.getContext();

            final AuthorWork work = mVm.getWorks().get(position);

            holder.vb.title.setText(work.getLabel(context));

            final PartialDate date = work.getFirstPublicationDate();
            if (date.isEmpty()) {
                holder.vb.year.setVisibility(View.GONE);
            } else {
                // screen space is at a premium here, and books can have 'yyyy-mm-dd' dates,
                // cut the date to just the year.
                final String fp = context.getString(R.string.brackets,
                                                    String.valueOf(date.getYearValue()));
                holder.vb.year.setText(fp);
                holder.vb.year.setVisibility(View.VISIBLE);
            }

            switch (work.getWorkType()) {
                case AuthorWork.TYPE_TOC: {
                    // show the icon if this entry appears in more than one book in our collection
                    if (work.getBookCount() > 1) {
                        holder.vb.btnType.setImageDrawable(mTocEntryIcon);
                        holder.vb.btnType.setVisibility(View.VISIBLE);
                        holder.vb.btnType.setContentDescription(mMultipleBooksStr);
                    } else {
                        holder.vb.btnType.setVisibility(View.INVISIBLE);
                    }
                    break;
                }
                case AuthorWork.TYPE_BOOK: {
                    holder.vb.btnType.setImageDrawable(mBookEntryIcon);
                    holder.vb.btnType.setVisibility(View.VISIBLE);
                    holder.vb.btnType.setContentDescription(mBookStr);
                    break;
                }
                default: {
                    // we should never get here... flw
                    holder.vb.btnType.setVisibility(View.INVISIBLE);
                    break;
                }
            }
        }

        @Override
        public int getItemCount() {
            return mVm.getWorks().size();
        }

        @NonNull
        @Override
        public String[] getPopupText(final int position) {
            final String title = mVm.getWorks().get(position)
                                    .getLabel(mInflater.getContext());
            return new String[]{title};
        }
    }
}
