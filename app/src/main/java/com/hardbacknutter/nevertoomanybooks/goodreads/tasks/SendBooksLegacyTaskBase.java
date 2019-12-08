/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.EditBookActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.BindableItemCursor;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.ContextDialogItem;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.Event;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.EventsCursor;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

/**
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
abstract class SendBooksLegacyTaskBase
        extends TQTask {

    private static final long serialVersionUID = 5950331916209877890L;

    /** wait time before declaring network failure. */
    private static final int FIVE_MINUTES = 300;
    /** Number of books with no ISBN. */
    int mNoIsbn;
    /** Number of books that had ISBN but could not be found. */
    int mNotFound;
    /** Number of books successfully sent. */
    int mSent;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    SendBooksLegacyTaskBase(@NonNull final String description) {
        super(description);
    }

    /**
     * Run the task, log exceptions.
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager) {
        Context localContext = App.getLocalizedAppContext();
        try {
            NetworkUtils.poke(localContext,
                              GoodreadsManager.BASE_URL,
                              GoodreadsManager.SOCKET_TIMEOUT_MS);

            GoodreadsManager grManager = new GoodreadsManager();
            if (grManager.hasValidCredentials()) {
                return send(queueManager, localContext, grManager);
            }
        } catch (@NonNull final IOException ignore) {
            // Only wait 5 minutes max on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
        }

        return false;
    }

    /**
     * @param context   Current context
     * @param grManager the Goodreads Manager
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    protected abstract boolean send(@NonNull QueueManager queueManager,
                                    @NonNull Context context,
                                    @NonNull GoodreadsManager grManager);

    /**
     * Try to export one book.
     *
     * @param context   Current context
     * @param grManager the Goodreads Manager
     * @param db        Database Access
     *
     * @return {@code false} on failure, {@code true} on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(@NonNull final QueueManager queueManager,
                        @NonNull final Context context,
                        @NonNull final GoodreadsManager grManager,
                        @NonNull final DAO db,
                        @NonNull final BookCursor bookCursor) {

        GoodreadsManager.ExportResult result;
        Exception exportException = null;
        try {
            result = grManager.sendOneBook(context, db, bookCursor);

        } catch (@NonNull final CredentialsException e) {
            result = GoodreadsManager.ExportResult.credentialsError;
            exportException = e;
        } catch (@NonNull final BookNotFoundException e) {
            result = GoodreadsManager.ExportResult.notFound;
            exportException = e;
        } catch (@NonNull final IOException e) {
            result = GoodreadsManager.ExportResult.ioError;
            exportException = e;
        } catch (@NonNull final RuntimeException e) {
            result = GoodreadsManager.ExportResult.error;
            exportException = e;
        }

        long bookId = bookCursor.getLong(DBDefinitions.KEY_PK_ID);

        switch (result) {
            case sent:
                // Record the change
                db.setGoodreadsSyncDate(bookId);
                mSent++;
                break;

            case noIsbn:
                storeEvent(new GrNoIsbnEvent(context, bookId));
                mNoIsbn++;
                break;

            case notFound:
                storeEvent(new GrNoMatchEvent(context, bookId));
                mNotFound++;
                break;

            case ioError:
                // Only wait 5 minutes on network errors.
                if (getRetryDelay() > FIVE_MINUTES) {
                    setRetryDelay(FIVE_MINUTES);
                }
                queueManager.updateTask(this);
                return false;

            case credentialsError:
            case error:
                setException(exportException);
                queueManager.updateTask(this);
                return false;
        }

        return true;
    }

    /**
     * All Event objects resulting from sending books to Goodreads.
     */
    private static class GrSendBookEvent
            extends Event {

        private static final long serialVersionUID = 8056090158313083738L;
        private final long mBookId;

        /**
         * Constructor.
         *
         * @param description Description of this event.
         * @param bookId      ID of related book.
         */
        GrSendBookEvent(@NonNull final String description,
                        final long bookId) {
            super(description);
            mBookId = bookId;
        }

        /**
         * Get the related Book ID.
         *
         * @return id of related book.
         */
        long getBookId() {
            return mBookId;
        }

        /**
         * Resubmit this book and delete this event.
         *
         * @param context Current context
         */
        void retry(@NonNull final Context context) {
            QueueManager qm = QueueManager.getQueueManager();
            SendOneBookLegacyTask task = new SendOneBookLegacyTask(
                    context.getString(R.string.gr_send_book_to_goodreads, mBookId),
                    mBookId);
            qm.enqueueTask(task, QueueManager.Q_SMALL_JOBS);
            qm.deleteEvent(getId());
        }

        /**
         * Return a view capable of displaying basic book event details,
         * ideally usable by all BookEvent subclasses.
         * This method also prepares the BookEventHolder object for the View.
         * <p>
         * <p>
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public View getView(@NonNull final Context context,
                            @NonNull final BindableItemCursor cursor,
                            @NonNull final ViewGroup parent) {
            View view = LayoutInflater.from(context)
                                      .inflate(R.layout.row_event_info, parent, false);
            view.setTag(R.id.TAG_GR_EVENT, this);
            BookEventHolder holder = new BookEventHolder(view);
            holder.event = this;
            holder.rowId = cursor.getId();

            holder.buttonView.setTag(R.id.TAG_GR_BOOK_EVENT_HOLDER, holder);
            holder.retryView.setTag(R.id.TAG_GR_BOOK_EVENT_HOLDER, holder);

            return view;
        }

        @Override
        public void bindView(@NonNull final View view,
                             @NonNull final Context context,
                             @NonNull final BindableItemCursor cursor,
                             @NonNull final DAO db) {
            final EventsCursor eventsCursor = (EventsCursor) cursor;

            // Update event info binding; the Views in the holder are unchanged,
            // but when it is reused the Event and id will change.
            BookEventHolder holder = (BookEventHolder) view.getTag(R.id.TAG_GR_BOOK_EVENT_HOLDER);
            holder.event = this;
            holder.rowId = eventsCursor.getId();

            ArrayList<Author> authors = db.getAuthorsByBookId(mBookId);
            String authorName;
            if (!authors.isEmpty()) {
                authorName = authors.get(0).getLabel(context);
                if (authors.size() > 1) {
                    authorName = authorName + ' ' + context.getString(R.string.and_others);
                }
            } else {
                authorName = context.getString(R.string.unknown).toUpperCase(Locale.getDefault());
            }

            String title = db.getBookTitle(mBookId);
            if (title == null) {
                title = context.getString(R.string.warning_book_no_longer_exists);
            }

            holder.titleView.setText(title);
            holder.authorView.setText(context.getString(R.string.lbl_by_author_s, authorName));
            holder.errorView.setText(getDescription());

            String date = DateUtils.toPrettyDateTime(eventsCursor.getEventDate());
            holder.dateView.setText(context.getString(R.string.gr_tq_occurred_at, date));

            holder.retryView.setVisibility(View.GONE);

            holder.buttonView.setChecked(eventsCursor.isSelected());
            holder.buttonView.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        BookEventHolder bookEventHolder =
                                (BookEventHolder) buttonView.getTag(R.id.TAG_GR_BOOK_EVENT_HOLDER);
                        eventsCursor.setSelected(bookEventHolder.rowId, isChecked);
                    });

            // get book details
            String isbn = db.getBookIsbn(mBookId);
            // Hide parts of view based on current book having an isbn or not..
            if (isbn != null && !isbn.isEmpty()) {
                holder.retryView.setVisibility(View.VISIBLE);
                holder.retryView.setTag(R.id.TAG_GR_BOOK_EVENT_HOLDER, this);
                holder.retryView.setOnClickListener(v -> {
                    BookEventHolder h = (BookEventHolder) v.getTag(R.id.TAG_GR_BOOK_EVENT_HOLDER);
                    h.event.retry(v.getContext());
                });
                return;
            }
            holder.retryView.setVisibility(View.GONE);
        }

        @Override
        public void addContextMenuItems(@NonNull final Context context,
                                        @NonNull final View view,
                                        final long id,
                                        @NonNull final List<ContextDialogItem> items,
                                        @NonNull final DAO db) {

            // EDIT BOOK
            items.add(new ContextDialogItem(
                    context.getString(R.string.menu_edit),
                    () -> {
                        try {
                            GrSendBookEvent event =
                                    (GrSendBookEvent) view.getTag(R.id.TAG_GR_EVENT);
                            Intent intent = new Intent(context, EditBookActivity.class)
                                    .putExtra(DBDefinitions.KEY_PK_ID,
                                              event.getBookId());
                            context.startActivity(intent);
                        } catch (@NonNull final RuntimeException ignore) {
                            // not a book event?
                        }
                    }));

            // ENHANCE: Reinstate Goodreads search when Goodreads work.editions is available
//            // SEARCH GOODREADS
//            items.add(new ContextDialogItem(
//                    context.getString(R.string.progress_msg_searching_site,
//                                      context.getString(R.string.goodreads)), () -> {
//                BookEventHolder holder = (BookEventHolder)
//                        view.getTag(R.id.TAG_GR_BOOK_EVENT_HOLDER);
//                Intent intent = new Intent(context, GoodreadsSearchActivity.class)
//                        .putExtra(DBDefinitions.KEY_PK_ID, holder.event.getId());
//                context.startActivity(intent);
//            }));

            // DELETE EVENT
            items.add(new ContextDialogItem(context.getString(R.string.gr_tq_menu_delete_event),
                                            () -> QueueManager.getQueueManager().deleteEvent(id)));

            // RETRY EVENT
            String isbn = db.getBookIsbn(mBookId);
            if (isbn != null && !isbn.isEmpty()) {
                items.add(new ContextDialogItem(
                        context.getString(R.string.retry),
                        () -> {
                            try {
                                GrSendBookEvent event = (GrSendBookEvent)
                                        view.getTag(R.id.TAG_GR_EVENT);
                                event.retry(context);
                                QueueManager.getQueueManager().deleteEvent(id);
                            } catch (@NonNull final RuntimeException ignore) {
                                // not a book event?
                            }
                        }));
            }
        }

        /**
         * Class to implement the holder model for view we create.
         */
        static class BookEventHolder {

            final TextView titleView;
            final TextView authorView;
            final CompoundButton buttonView;
            final TextView errorView;
            final TextView dateView;
            final Button retryView;

            long rowId;
            GrSendBookEvent event;

            BookEventHolder(@NonNull final View view) {
                titleView = view.findViewById(R.id.title);
                authorView = view.findViewById(R.id.author);
                buttonView = view.findViewById(R.id.cbx_checked);
                dateView = view.findViewById(R.id.date);
                errorView = view.findViewById(R.id.error);
                retryView = view.findViewById(R.id.retry);

                view.setTag(R.id.TAG_GR_BOOK_EVENT_HOLDER, this);
            }
        }

    }

    /**
     * Exception indicating the book's ISBN could not be found at Goodreads.
     */
    private static class GrNoMatchEvent
            extends GrSendBookEvent
            implements TipManager.TipOwner {


        private static final long serialVersionUID = 8764019100842546976L;

        GrNoMatchEvent(@NonNull final Context context,
                       final long bookId) {
            super(context.getString(R.string.warning_no_matching_book_found), bookId);
        }

        @Override
        @StringRes
        public int getTip() {
            return R.string.gr_info_no_match;
        }

    }

    /**
     * Exception indicating the book's ISBN was blank.
     */
    private static class GrNoIsbnEvent
            extends GrSendBookEvent
            implements TipManager.TipOwner {


        private static final long serialVersionUID = -8208929167310932723L;

        GrNoIsbnEvent(@NonNull final Context context,
                      final long bookId) {
            super(context.getString(R.string.warning_no_isbn_stored_for_book), bookId);
        }

        @Override
        @StringRes
        public int getTip() {
            return R.string.gr_info_no_isbn;
        }

    }
}
