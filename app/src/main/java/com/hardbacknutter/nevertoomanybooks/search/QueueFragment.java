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
package com.hardbacknutter.nevertoomanybooks.search;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.Group;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogBookFoundBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.search.queue.QueueViewModel;
import com.hardbacknutter.nevertoomanybooks.search.queue.QueuedItem;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.util.logger.LoggerFactory;

public abstract class QueueFragment
        extends SearchBookBaseFragment {

    private static final String TAG = "QueueFragment";

    /**
     * MIME types for importing a list of codes from a file.
     *
     * @see com.hardbacknutter.nevertoomanybooks.backup.ImportFragment
     */
    private static final String ANY_URI = "*/*";

    /** Handles the queue. */
    private QueueViewModel qvm;

    /**
     * Intercept 'back' when there are items in the queue still being searched for.
     * By default, disabled.
     *
     * @see #onQueueUpdated(Iterator)
     */
    private final OnBackPressedCallback backPressedWithActiveSearches =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    new MaterialAlertDialogBuilder(getContext())
                            .setTitle(R.string.confirm_leave_code_search)
                            .setSingleChoiceItems(R.array.lbl_leave_search_options,
                                                  -1, (d, option) -> {
                                        d.dismiss();
                                        // option 0: Save for later
                                        // option 1: Clear queue
                                        final boolean clear = option == 1;
                                        qvm.clear(coordinator, clear);
                                        //noinspection DataFlowIssue
                                        getActivity().setResult(Activity.RESULT_OK,
                                                                createResultIntent());
                                        getActivity().finish();
                                    })
                            .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                            .create()
                            .show();
                }
            };

    /** The user wants to import a list of codes to the queue. */
    private ActivityResultLauncher<String> openUriLauncher;

    /** Group of all queue related widgets. Used to set visibility. */
    private Group vbQueueGroup;
    /** The list of chips/items. */
    private ChipGroup vbQueue;
    /** Rotating hourglass progress indicator. */
    private ImageView vbQueueProgress;

    /** Set in {@link #loadStoredQueue(int, int, Runnable)}. */
    private Style style;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        openUriLauncher = registerForActivityResult(new GetContentUriForReadingContract(),
                                                    o -> o.ifPresent(this::onOpenUri));
    }

    /**
     * Initialize the {@link QueueViewModel}.
     * <p>
     * Must be called by the child class from {@link #onCreate(Bundle)}.
     *
     * @param clazz for the {@link QueueViewModel}.
     * @param style for displaying info about the found books.
     */
    @SuppressWarnings("SameParameterValue")
    void initQueue(@NonNull final Class<? extends QueueViewModel> clazz,
                   @NonNull final Style style) {

        qvm = new ViewModelProvider(this).get(clazz);
        qvm.init();

        this.style = style;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedWithActiveSearches);

        vbQueueGroup = view.findViewById(R.id.queue_group);
        vbQueue = view.findViewById(R.id.queue);
        vbQueueProgress = view.findViewById(R.id.queue_progress);

        final MaterialButton vbBtnClearQueue = view.findViewById(R.id.btn_clear_queue);
        vbBtnClearQueue.setOnClickListener(v -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            qvm.clear(coordinator, true);
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        updateQueue();
    }

    /**
     * Must be called by the child class at the end of {@link #onViewCreated(View, Bundle)}.
     *
     * @param dialogTitle           String res id
     * @param confirmQueueHasXItems Plural res id
     * @param afterOnViewCreated    Runnable to be called if there either
     *                              is no stored queue or if the user discards the stored queue
     */
    void loadStoredQueue(@StringRes final int dialogTitle,
                         @PluralsRes final int confirmQueueHasXItems,
                         @NonNull final Runnable afterOnViewCreated) {

        qvm.onUpdate().observe(getViewLifecycleOwner(), this::onQueueUpdated);

        final Context context = getContext();
        final List<QueuedItem> items = qvm.readFromPreferences();
        if (items.isEmpty()) {
            afterOnViewCreated.run();
        } else {
            final int size = items.size();
            //noinspection DataFlowIssue
            final String msg = context.getResources().getQuantityString(confirmQueueHasXItems,
                                                                        size, size);
            new MaterialAlertDialogBuilder(context)
                    .setTitle(dialogTitle)
                    .setMessage(msg)
                    .setNeutralButton(R.string.action_delete, (d, w) -> {
                        qvm.clearPreferences();
                        afterOnViewCreated.run();
                        d.dismiss();
                    })
                    .setPositiveButton(R.string.action_search, (d, w) -> startSearch(items))
                    .create()
                    .show();
        }
    }

    /**
     * Show the {@link Uri} picker dialog.
     *
     * @see #onOpenUri(Uri)
     */
    @CallSuper
    void importFromFile() {
        openUriLauncher.launch(ANY_URI);
    }

    /**
     * Import a list of codes from the given {@link Uri}.
     *
     * @param uri as chosen by the user
     */
    private void onOpenUri(@NonNull final Uri uri) {
        try {
            //noinspection DataFlowIssue
            final List<QueuedItem> items = qvm.readFromFile(getContext(), uri);
            if (!items.isEmpty()) {
                startSearch(items);
            }
        } catch (@NonNull final IOException e) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_import_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Start searching the given list (coming from prefs or a file).
     *
     * @param items to search for
     */
    private void startSearch(@NonNull final List<QueuedItem> items) {
        // Do NOT switch on the ScanMode.Batch or otherwise
        // but DO hide the progress
        setEnableProgressMessages(false);
        final boolean searchStarted = qvm.add(items, this::startSearch);

        if (searchStarted) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.progress_msg_searching,
                          Snackbar.LENGTH_SHORT).show();
        } else {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_book_search_failed,
                          Snackbar.LENGTH_LONG).show();
        }
        inputFieldRequestFocus();
    }

    /**
     * Start a search for the given code.
     *
     * @param productCode to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     */
    abstract int startSearch(@NonNull ProductCode productCode);

    /**
     * Implementation must call {@code inputField#requestFocus()}.
     */
    abstract void inputFieldRequestFocus();

    /**
     * Implementation must call {@code inputField#setError(...)}.
     */
    abstract void inputFieldSetError();

    /**
     * Check if the queue has any elements.
     *
     * @return {@code true} if there is at least one item in the queue
     */
    boolean isQueuePopulated() {
        return qvm.getSize() > 0;
    }

    /**
     * Add to the queue and start a search.
     *
     * @param productCode to add
     *
     * @return the searchId, or:
     *         {@link QueueViewModel#SEARCH_NOT_STARTED} if no search was started.
     *         This is not necessarily an error;
     *         {@link QueueViewModel#SEARCH_DUPLICATE_ITEM} if the item was already present.
     */
    int addToQueue(@NonNull final ProductCode productCode) {
        return qvm.add(new QueuedItem(productCode), this::startSearch);
    }

    /**
     * Trigger a UI update with the latest queue content.
     */
    void updateQueue() {
        onQueueUpdated(qvm.iterator());
    }

    /**
     * Called when the queue has changed: refresh the all queue related views/buttons.
     *
     * @param list to display; can be empty
     */
    private void onQueueUpdated(@NonNull final Iterator<QueuedItem> list) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onQueueUpdated");
        }

        // TODO: this can cause flicker if the updates comes too fast
        if (isQueuePopulated()) {
            vbQueue.removeAllViews();
        }

        while (list.hasNext()) {
            final QueuedItem item = list.next();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "onQueueUpdated", "item=" + item);
            }

            final Chip chip = new Chip(getContext(), null, R.attr.appChipInputStyle);
            // RTL-friendly Chip Layout
            chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            chip.setTag(item);
            chip.setText(item.getProductCode().asText());
            chip.setCheckable(false);
            chip.setOnCloseIconClickListener(this::removeFromQueue);
            chip.setOnClickListener(this::onQueueItemClicked);
            // experimented with adding an icon depending on the below state,
            // but that becomes the 'checkable' icon and interferes with clicking.
            // And it all becomes a bit cluttered... using separate 'hourglass' now.

            final BookSearchResult result = item.getResult();
            if (result != null) {
                if (result.hasErrors()) {
                    if (result.hasBook()) {
                        // There was an error, but the book has (some) usable data
                        chip.setChipBackgroundColorResource(
                                R.color.queue_search_success_with_partial_errors);
                    } else {
                        // There was an error, and the book has no useful data
                        chip.setChipBackgroundColorResource(R.color.queue_search_failure);
                    }
                } else if (result.hasBook()) {
                    // no error, and the book has usable data
                    chip.setChipBackgroundColorResource(R.color.queue_search_success);
                }
            }
            vbQueue.addView(chip);
        }

        updateQueueViewsVisibility();
    }

    /**
     * The user tapped a queue chip.
     * The chip status can be:
     * <ol>
     *     <li>Newly added, a search has not been started.
     *         <br>The code will be available.
     *         <br>No other data/errors.
     *     </li>
     *     <li>A search is running.
     *         <br>There may be errors available.
     *         <br>There may or may not be enough data for a Book.
     *     </li>
     *     <li>The search was finished.
     *         <br>There may be errors available.
     *          <br>A Book was not found.
     *     </li>
     *     <li>The search was finished.
     *         <br>There may be errors available.
     *         <br>A Book was found.
     *     </li>
     * </ol>
     *
     * @param chip clicked
     */
    private void onQueueItemClicked(@NonNull final View chip) {
        @SuppressWarnings("unchecked")
        final QueuedItem item = (QueuedItem) chip.getTag();
        @Nullable
        final BookSearchResult result = item.getResult();
        final boolean hasErrors = result != null && result.hasErrors();
        final boolean hasBook = result != null && result.hasBook();

        final Context context = getContext();
        final DialogBookFoundBinding dvb = DialogBookFoundBinding.inflate(getLayoutInflater());
        //noinspection DataFlowIssue
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setView(dvb.getRoot());

        // Show any available book information.
        // This is done in all possible status variations of the item.
        final String info;
        if (hasBook) {
            final StringJoiner sj = new StringJoiner("\n");
            sj.add(item.getProductCode().asText());

            final Book book = result.getBook();
            final Author primaryAuthor = book.getPrimaryAuthor();
            if (primaryAuthor != null) {
                sj.add(primaryAuthor.getLabel(context, Details.Normal, this.style));
            }

            sj.add(book.getTitle());
            info = sj.toString();
        } else {
            // No result (yet); the search is ongoing
            // or there was a result, but not enough data to constitute a Book
            info = item.getProductCode().asText();
        }
        dvb.info.setText(info);

        // Potentially show an error message in the dialog body.
        // This is done in all possible status variations of the item.
        if (hasErrors) {
            dvb.errorMessage.setText(String.join("\n", result.getErrors(context)));
        }
        dvb.errorMessage.setVisibility(hasErrors ? View.VISIBLE : View.GONE);


        // 1. Newly added, a search has not been started.
        // OR
        // 2. A search is running. A Book may or may not be available (we disregard this here).
        if (result == null || item.isSearching()) {
            dvb.edit.setVisibility(View.GONE);

            builder.setTitle(R.string.progress_msg_searching)
                   // Discard this item entirely
                   .setNeutralButton(R.string.action_discard, (d, w) -> {
                       d.dismiss();
                       removeFromQueue(chip);
                   })
                   // Close the dialog and continue
                   .setNegativeButton(R.string.action_continue, (d, w) -> d.dismiss())
                   .create()
                   .show();

            return;
        }

        // 3. Search was finished and we do NOT have (enough) data for a Book.
        if (!hasBook) {
            final AlertDialog dialog = builder
                    // Discard this item entirely
                    .setNeutralButton(R.string.action_discard, (d, w) -> {
                        d.dismiss();
                        removeFromQueue(chip);
                    })
                    // ENHANCE: replace with RETRY **if** the errors are retriable.
                    // Close the dialog, but leave the item as-is
                    .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                    .create();

            // We offer "edit" to allow manual entry
            dvb.edit.setVisibility(View.VISIBLE);
            dvb.edit.setOnClickListener(v -> {
                dialog.dismiss();
                removeFromQueue(chip);
                onSearchResults(result);
            });

            dialog.show();
            return;
        }

        // Search was finished and we have a Book.
        // All choices: "edit", "save", "discard", "cancel".
        final AlertDialog dialog = builder
                // Discard this item entirely
                .setNeutralButton(R.string.action_discard, (d, w) -> {
                    d.dismiss();
                    removeFromQueue(chip);
                })
                // Close the dialog, but leave the item as-is
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                // Save without editing
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    d.dismiss();
                    removeFromQueue(chip);
                    onSearchResultsSaveBook(result);
                })
                .create();

        // Edit before save or discard from the edit screen
        dvb.edit.setVisibility(View.VISIBLE);
        dvb.edit.setOnClickListener(v -> {
            dialog.dismiss();
            removeFromQueue(chip);
            onSearchResults(result);
        });

        dialog.show();
    }

    @Override
    void onSearchFinished(@NonNull final BookSearchResult result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onSearchFinished",
                                        "queue=" + qvm.getSize(),
                                        result);
        }

        if (qvm.getSize() == 0
            // Check the scan-mode as it was used for **this** scan-result!
            && (result.getScanMode() == ScanMode.Off
                || result.getScanMode() == ScanMode.Continuous)) {
            // user interactive; we'll end up in #onSearchResults
            super.onSearchFinished(result);
        } else {
            // we'll end up in #onQueueUpdated
            qvm.onResult(result);
        }
    }

    /**
     * Called when the user tapped a queued item, and choose "Save" (without editing).
     *
     * @param result results of the search
     *
     * @see #onSearchResultsSaveBook(Book)
     */
    private void onSearchResultsSaveBook(@NonNull final BookSearchResult result) {
        // Paranoia check
        if (!result.hasBook()) {
            // We should never get here... flw
            inputFieldSetError();
            return;
        }

        final Book book = result.getBook();

        // Add it to the current shelf.
        book.ensureBookshelf();

        // check for duplicates
        final String isbnStr = book.getIsbn();
        if (!isbnStr.isEmpty()) {
            final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
            // all codes accepted, including invalid ones
            if (bookDao.bookExists(ISBN.parse(isbnStr))) {
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.warning_24px)
                        .setTitle(R.string.lbl_duplicate_book)
                        .setMessage(R.string.confirm_duplicate_book_message)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                        .setNeutralButton(R.string.action_edit, (d, w) -> onSearchResults(result))
                        // add regardless
                        .setPositiveButton(R.string.action_add,
                                           (d, w) -> onSearchResultsSaveBook(book))
                        .create()
                        .show();
                return;
            }
        }

        // not a duplicate, drop through
        onSearchResultsSaveBook(book);
    }

    /**
     * Called when the user tapped a queued item, and choose "Save" (without editing).
     *
     * @param book results of the search
     */
    abstract void onSearchResultsSaveBook(@NonNull Book book);

    /**
     * Common code to remove the given chip and cancel any active searches it owns.
     *
     * @param chip to remove
     */
    private void removeFromQueue(@NonNull final View chip) {
        @SuppressWarnings("unchecked")
        final QueuedItem item = (QueuedItem) chip.getTag();
        // remove but update the view manually to avoid flicker
        qvm.remove(coordinator, item);
        vbQueue.removeView(chip);
        updateQueueViewsVisibility();
    }

    /**
     * Common code to update the visibility of the UI queue.
     */
    private void updateQueueViewsVisibility() {
        vbQueueGroup.setVisibility(isQueuePopulated() ? View.VISIBLE : View.GONE);

        final boolean searching = qvm.isSearching();
        if (searching) {
            vbQueueProgress.setVisibility(View.VISIBLE);
            vbQueueProgress.startAnimation(
                    AnimationUtils.loadAnimation(getContext(), R.anim.rotate));
        } else {
            vbQueueProgress.setVisibility(View.GONE);
            vbQueueProgress.clearAnimation();
        }

        // Enabled when there is a non-empty queue.
        // We don't look for 'searching' or 'results' items
        backPressedWithActiveSearches.setEnabled(qvm.getSize() > 0);
    }
}
