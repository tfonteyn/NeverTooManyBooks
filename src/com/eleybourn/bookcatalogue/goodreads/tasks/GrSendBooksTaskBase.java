package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.EditBookActivity;
import com.eleybourn.bookcatalogue.EditBookFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.goodreads.api.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.BindableItemCursor;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.ContextDialogItem;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.Event;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.EventsCursor;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.BaseTask;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
abstract class GrSendBooksTaskBase
        extends BaseTask {

    private static final long serialVersionUID = -8519158637447641604L;
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
    GrSendBooksTaskBase(@NonNull final String description) {
        super(description);
    }

    /**
     * Run the task, log exceptions.
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager,
                       @NonNull final Context context) {
        boolean result = false;

        if (NetworkUtils.isAlive(GoodreadsManager.BASE_URL)) {
            GoodreadsManager grManager = new GoodreadsManager();
            // Ensure we are allowed
            if (grManager.hasValidCredentials()) {
                result = send(queueManager, context, grManager);
            } else {
                Logger.warnWithStackTrace(this, "no valid credentials");
            }
        } else {
            // Only wait 5 minutes max on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
            Logger.warn(this, "run", "network or site not available");
        }

        return result;
    }

    /**
     * @return {@code false} to requeue, {@code true} for success
     */
    protected abstract boolean send(@NonNull QueueManager queueManager,
                                    @NonNull Context context,
                                    @NonNull GoodreadsManager grManager);

    /**
     * Try to export one book.
     *
     * @return {@code false} on failure, {@code true} on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(@NonNull final QueueManager queueManager,
                        @NonNull final Context context,
                        @NonNull final GoodreadsManager grManager,
                        @NonNull final DAO db,
                        @NonNull final BookCursorRow bookCursorRow) {

        GoodreadsManager.ExportDisposition disposition;
        Exception exportException = null;
        try {
            disposition = grManager.sendOneBook(db, bookCursorRow);
        } catch (@NonNull final BookNotFoundException | AuthorizationException | IOException e) {
            disposition = GoodreadsManager.ExportDisposition.error;
            exportException = e;
        }

        long bookId = bookCursorRow.getId();

        // Handle the result
        switch (disposition) {
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

            case error:
                setException(exportException);
                queueManager.updateTask(this);
                return false;

            case networkError:
                // Only wait 5 minutes on network errors.
                if (getRetryDelay() > FIVE_MINUTES) {
                    setRetryDelay(FIVE_MINUTES);
                }
                queueManager.updateTask(this);
                return false;
        }
        return true;
    }

    /**
     * All Event objects resulting from sending books to goodreads.
     *
     * @author Philip Warner
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
         * @return ID of related book.
         */
        long getBookId() {
            return mBookId;
        }

        /**
         * Resubmit this book and delete this event.
         */
        void retry(@NonNull final Context context) {
            QueueManager qm = QueueManager.getQueueManager();
            GrSendOneBookTask task = new GrSendOneBookTask(
                    context.getString(R.string.gr_send_book_to_goodreads, mBookId),
                    mBookId);
            qm.enqueueTask(task, QueueManager.Q_SMALL_JOBS);
            qm.deleteEvent(getId());
        }

        /**
         * Return a view capable of displaying basic book event details,
         * ideally usable by all BookEvent subclasses.
         * This method also prepares the BookEventHolder object for the View.
         */
        @Override
        @NonNull
        public View getView(@NonNull final LayoutInflater inflater,
                            @NonNull final BindableItemCursor cursor,
                            @NonNull final ViewGroup parent) {
            View view = inflater.inflate(R.layout.row_event_info, parent, false);
            view.setTag(R.id.TAG_GR_EVENT, this);
            BookEventHolder holder = new BookEventHolder(view);
            holder.event = this;
            holder.rowId = cursor.getId();

            holder.buttonView.setTag(R.id.TAG_GR_BOOK_EVENT_HOLDER, holder);
            holder.retryView.setTag(R.id.TAG_GR_BOOK_EVENT_HOLDER, holder);

            return view;
        }

        /**
         * Display the related book details in the passed View object.
         */
        @Override
        public void bindView(@NonNull final View view,
                             @NonNull final Context context,
                             @NonNull final BindableItemCursor cursor,
                             @NonNull final DAO db) {
            final EventsCursor eventsCursor = (EventsCursor) cursor;

            // Update event info binding; the Views in the holder are unchanged,
            // but when it is reused the Event and ID will change.
            BookEventHolder holder = (BookEventHolder) view.getTag(R.id.TAG_GR_BOOK_EVENT_HOLDER);
            holder.event = this;
            holder.rowId = eventsCursor.getId();

            ArrayList<Author> authors = db.getAuthorsByBookId(mBookId);
            String author;
            if (!authors.isEmpty()) {
                author = authors.get(0).getLabel();
                if (authors.size() > 1) {
                    author = author + ' ' + context.getString(R.string.and_others);
                }
            } else {
                author = context.getString(R.string.unknown).toUpperCase();
            }

            String title = db.getBookTitle(mBookId);
            if (title == null) {
                title = context.getString(R.string.warning_book_was_deleted_uc);
            }

            holder.titleView.setText(title);
            holder.authorView.setText(context.getString(R.string.lbl_by_author_s, author));
            holder.errorView.setText(getDescription());

            String date = DateUtils.toPrettyDateTime(LocaleUtils.from(context),
                                                     eventsCursor.getEventDate());
            holder.dateView.setText(context.getString(R.string.gr_tq_occurred_at, date));

            holder.retryView.setVisibility(View.GONE);

            holder.buttonView.setChecked(eventsCursor.isSelected());
            holder.buttonView.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        BookEventHolder bookEventHolder = (BookEventHolder)
                                buttonView.getTag(R.id.TAG_GR_BOOK_EVENT_HOLDER);
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

        /**
         * Add ContextDialogItems relevant for the specific book the selected View is
         * associated with.
         * Subclass can override this and add items at end/start or just replace these completely.
         */
        @Override
        public void addContextMenuItems(@NonNull final Context context,
                                        @NonNull final AdapterView<?> parent,
                                        @NonNull final View view,
                                        final int position,
                                        final long id,
                                        @NonNull final List<ContextDialogItem> items,
                                        @NonNull final DAO db) {

            // EDIT BOOK
            items.add(new ContextDialogItem(
                    context.getString(R.string.menu_edit_book),
                    () -> {
                        try {
                            GrSendBookEvent event = (GrSendBookEvent) view.getTag(R.id.TAG_GR_EVENT);
                            Intent intent = new Intent(context, EditBookActivity.class)
                                    .putExtra(DBDefinitions.KEY_ID, event.getBookId())
                                    .putExtra(EditBookFragment.REQUEST_BKEY_TAB,
                                              EditBookFragment.TAB_EDIT);
                            context.startActivity(intent);
                        } catch (@NonNull final RuntimeException ignore) {
                            // not a book event?
                        }
                    }));
            // ENHANCE: Reinstate goodreads search when goodreads work.editions API is available
//            // SEARCH GOODREADS
//            items.add(new ContextDialogItem(
//                    context.getString(R.string.searching_goodreads), () -> {
//                BookEventHolder holder = (BookEventHolder)
//                        view.getTag(R.id.TAG_BOOK_EVENT_HOLDER);
//                Intent intent = new Intent(context, GoodreadsSearchCriteriaActivity.class)
//                        .putExtra(DBDefinitions.KEY_ID, holder.event.getId());
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
         * Class to implement the 'holder' model for view we create.
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
                buttonView = view.findViewById(R.id.checked);
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
            implements HintManager.HintOwner {


        private static final long serialVersionUID = 8764019100842546976L;

        GrNoMatchEvent(@NonNull final Context context,
                       final long bookId) {
            super(context.getString(R.string.warning_no_matching_book_found), bookId);
        }

        @Override
        @StringRes
        public int getHint() {
            return R.string.gr_explain_goodreads_no_match;
        }

    }

    /**
     * Exception indicating the book's ISBN was blank.
     */
    private static class GrNoIsbnEvent
            extends GrSendBookEvent
            implements HintManager.HintOwner {


        private static final long serialVersionUID = -8208929167310932723L;

        GrNoIsbnEvent(@NonNull final Context context,
                      final long bookId) {
            super(context.getString(R.string.warning_no_isbn_stored_for_book), bookId);
        }

        @Override
        @StringRes
        public int getHint() {
            return R.string.gr_explain_goodreads_no_isbn;
        }

    }
}
