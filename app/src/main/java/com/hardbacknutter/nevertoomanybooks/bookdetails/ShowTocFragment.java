/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AuthorWorksContract;
import com.hardbacknutter.nevertoomanybooks.AuthorWorksInput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.DisplayBookLauncher;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentTocBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.FastScrollerMode;

// 2024-05-05: Tried using a SideSheetDialog on phone screens, but they are just that: a "Dialog"
// and not a DialogFragment. No life cycle entry points.
// This limits their use with a delegate (like we do for BottomSheet)
// to allow flexibility. Especially the integrated DisplayBookLauncher becomes hard to use.
public class ShowTocFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "ShowTocFragment";

    /** View Binding. */
    private FragmentTocBinding vb;

    private ShowTocViewModel vm;
    private ShowBookDetailsActivityViewModel aVm;

    /** Callback - used when we're running inside another component; e.g. the BoB. */
    @Nullable
    private BookChangedListener bookChangedListener;

    /** Display a Book. From there the user could edit it... so we must propagate the result. */
    private DisplayBookLauncher displayBookLauncher;
    /** View all works of an Author. */
    private ActivityResultLauncher<AuthorWorksInput> authorWorksLauncher;

    /** The Adapter. */
    private AuthorWorksAdapter adapter;

    /**
     * Constructor.
     *
     * @param book      to display
     * @param embedded  {@code true} when we're running embedded in the book-details fragment
     *                  or {@code false} as standalone.
     * @param bookshelf current Bookshelf displayed by the BoB
     *
     * @return instance
     */
    @NonNull
    public static Fragment create(@NonNull final Book book,
                                  final boolean embedded,
                                  @NonNull final Bookshelf bookshelf) {
        final Fragment fragment = new ShowTocFragment();
        final ShowTocInput input = new ShowTocInput(book.getId(), embedded, bookshelf);
        fragment.setArguments(input.toBundle());
        return fragment;
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        if (context instanceof BookChangedListener) {
            bookChangedListener = (BookChangedListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        displayBookLauncher = new DisplayBookLauncher(this, o -> o.ifPresent(data -> {
            if (data.isModified()) {
                // Needed when running inside the ViewPager to update the activity result data
                // Ignored if running im embedded mode, but keeping this future-proof
                aVm.setDataModified();
                // when running in embedded mode, update the BoB list
                if (bookChangedListener != null) {
                    bookChangedListener.onBookUpdated(vm.getBook(), (String) null);
                }
            }
        }));

        authorWorksLauncher = registerForActivityResult(
                new AuthorWorksContract(), o -> o.ifPresent(data -> {
                    if (data.isModified()) {
                        // cascade back to our own parent
                        aVm.setDataModified();
                    }
                    // always reload, easier and foolproof
                    vm.reloadBook();
                }));

        final ShowTocInput args = ShowTocInput.fromBundle(requireArguments());

        //noinspection DataFlowIssue
        aVm = new ViewModelProvider(getActivity()).get(ShowBookDetailsActivityViewModel.class);
        aVm.init(args.getBookshelf());

        if (args.isEmbedded()) {
            // If we're running in embedded mode, i.e. as a child-fragment, create the vm in
            // the parent fragment scope allowing it to be accessed by that parent.
            vm = new ViewModelProvider(requireParentFragment()).get(ShowTocViewModel.class);
        } else {
            // Otherwise, use local scope.
            vm = new ViewModelProvider(this).get(ShowTocViewModel.class);
        }

        vm.init(args);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentTocBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @CallSuper
    @SuppressLint({"ClickableViewAccessibility", "NotifyDataSetChanged"})
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // REMINDER: the FastScroller sets an Insets listener on the RecyclerView!

        final Context context = getContext();

        //noinspection DataFlowIssue
        FastScrollerMode.create(context).attach(vb.toc);

        adapter = new AuthorWorksAdapter(context, aVm.getStyle(), vm.getAuthors(), vm.getWorks());
        adapter.setOnRowClickListener((v, position) -> {
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            final TocEntry tocEntry = vm.getWorks().get(position);

            if (v.getId() == R.id.author) {
                authorWorksLauncher.launch(new AuthorWorksInput(
                        tocEntry.getPrimaryAuthor().getId(),
                        aVm.getBookshelf()));
            } else {
                // row/background: open the book
                // If there's only one book, there is no point doing this
                // as we're already on that book.
                if (tocEntry.getBookCount() > 1) {
                    displayBookLauncher.launch(this,
                                               vm.getWorks(), position,
                                               aVm.getBookshelf(), false);
                }
            }
        });

        vb.toc.setAdapter(adapter);
        vb.toc.setHasFixedSize(true);

        vm.onReloadBook().observe(getViewLifecycleOwner(), bookId -> {
            adapter.notifyDataSetChanged();
            updateToolbar();
        });

        updateToolbar();
    }

    private void updateToolbar() {
        if (!vm.isEmbedded()) {
            final Toolbar toolbar = getToolbar();
            //noinspection DataFlowIssue
            toolbar.setTitle(Author.getLabel(getContext(), vm.getAuthors()));
            toolbar.setSubtitle(vm.getScreenSubtitle());
        }
    }
}
