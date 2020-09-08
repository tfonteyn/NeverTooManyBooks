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
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBookDetailsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowTocEntryWithAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BooleanIndicatorAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EntityListChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.CsvFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.StringArrayResFormatter;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrSendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

/**
 * Class for representing read-only book details.
 * <p>
 * Keep in mind the fragment can be re-used.
 * Do NOT assume fields are empty by default when populating them manually.
 * <p>
 * Initializing the Fields is done in the ViewModel.
 */
public class BookDetailsFragment
        extends BookBaseFragment
        implements CoverHandler.HostingFragment {

    /** Log tag. */
    public static final String TAG = "BookDetailsFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_LENDER = EditLenderDialogFragment.TAG + ":rk";

    /** Registered with the Activity to deliver us gestures. */
    private View.OnTouchListener mOnTouchListener;
    /** Handle next/previous paging in the flattened booklist; called by mOnTouchListener. */
    private GestureDetector mGestureDetector;
    /** The details helper View model. */
    private BookDetailsFragmentViewModel mFragmentVM;
    /** View Binding. */
    private FragmentBookDetailsBinding mVb;
    /** Listen for changes coming from child (dialog) fragments. */
    private final BookChangedListener mBookChangedListener = (bookId, fieldsChanged, data) -> {
        if (data != null) {
            if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
                String loanee = data.getString(DBDefinitions.KEY_LOANEE);
                Objects.requireNonNull(loanee, ErrorMsg.NULL_INTENT_DATA);
                // the db was already updated, just update the book to avoid a reload.
                mBookViewModel.getBook().putString(DBDefinitions.KEY_LOANEE, loanee);
                populateLendToField(loanee);
            } else {
                // we don't expect/implement any others.
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "bookId=" + bookId + "|fieldsChanged=" + fieldsChanged);
                }
            }
        }
    };

    /** Goodreads send-book task. */
    private GrSendOneBookTask mGrSendOneBookTask;

    @NonNull
    @Override
    Fields getFields() {
        return mFragmentVM.getFields(getTag());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getChildFragmentManager()
                .setFragmentResultListener(RK_EDIT_LENDER, this, mBookChangedListener);

        mFragmentVM = new ViewModelProvider(this).get(BookDetailsFragmentViewModel.class);
        mFragmentVM.init(getArguments(), mBookViewModel.getBook());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        mVb = FragmentBookDetailsBinding.inflate(inflater, container, false);

        //noinspection ConstantConditions
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        // Anthology/TOC fields
        if (!DBDefinitions.isUsed(prefs, DBDefinitions.KEY_TOC_BITMASK)) {
            mVb.lblAnthology.setVisibility(View.GONE);
            mVb.iconAnthology.setVisibility(View.GONE);
            mVb.lblToc.setVisibility(View.GONE);
            mVb.toc.setVisibility(View.GONE);
            mVb.btnShowToc.setVisibility(View.GONE);
        }

        // Covers
        if (!DBDefinitions.isUsed(prefs, DBDefinitions.PREFS_IS_USED_THUMBNAIL)) {
            mVb.coverImage0.setVisibility(View.GONE);
            mVb.coverImage1.setVisibility(View.GONE);
        }

        if (!DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            mVb.lendTo.setVisibility(View.GONE);
        }

        return mVb.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mGrSendOneBookTask = new ViewModelProvider(this).get(GrSendOneBookTask.class);
        mGrSendOneBookTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mGrSendOneBookTask.onCancelled().observe(getViewLifecycleOwner(), this::onCancelled);
        mGrSendOneBookTask.onFailure().observe(getViewLifecycleOwner(), this::onGrFailure);
        mGrSendOneBookTask.onFinished().observe(getViewLifecycleOwner(), this::onGrFinished);

        // The FAB lives in the activity.
        //noinspection ConstantConditions
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        fab.setOnClickListener(v -> startEditBook());

        // ENHANCE: should be replaced by a ViewPager2/FragmentStateAdapter
        mGestureDetector = new GestureDetector(getContext(), new FlingHandler());
        mOnTouchListener = (v, event) -> mGestureDetector.onTouchEvent(event);

        // show/hide the TOC as the user flips the switch.
        mVb.btnShowToc.setOnClickListener(v -> {
            // note that the button is explicitly (re)set.
            // If user clicks to fast it seems to get out of sync.
            if (mVb.toc.getVisibility() == View.VISIBLE) {
                // force a scroll; a manual scroll is no longer possible after the TOC closes.
                mVb.rootScroller.fullScroll(View.FOCUS_UP);
                mVb.toc.setVisibility(View.GONE);
                mVb.btnShowToc.setChecked(false);

            } else {
                mVb.toc.setVisibility(View.VISIBLE);
                mVb.btnShowToc.setChecked(true);
            }
        });

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_view_only_help, null);
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.text != null) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), message.text, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onCancelled(@NonNull final LiveDataEvent message) {
        if (message.isNewEvent()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFailure(@NonNull final FinishedMessage<Exception> message) {
        if (message.isNewEvent()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), GrStatus.getMessage(getContext(), message.result),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFinished(@NonNull final FinishedMessage<GrStatus> message) {
        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
            if (message.result.getStatus() == GrStatus.FAILED_CREDENTIALS) {
                //noinspection ConstantConditions
                mGrAuthTask.prompt(getContext());
            } else {
                //noinspection ConstantConditions
                Snackbar.make(getView(), message.result.getMessage(getContext()),
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    void onInitFields(@NonNull final Fields fields) {

        final Context context = getContext();

        //noinspection ConstantConditions
        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);

        // These FieldFormatter's can be shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter();
        final FieldFormatter<String> htmlFormatter = new HtmlFormatter<>(true);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
        final FieldFormatter<String> languageFormatter = new LanguageFormatter();

        // book fields
        fields.add(R.id.title, new TextViewAccessor<>(), DBDefinitions.KEY_TITLE);

        fields.add(R.id.author, new TextViewAccessor<>(
                           new AuthorListFormatter(Author.Details.Full, false, true)),
                   Book.BKEY_AUTHOR_ARRAY, DBDefinitions.KEY_FK_AUTHOR)
              .setRelatedFields(R.id.lbl_author);

        fields.add(R.id.series_title, new TextViewAccessor<>(
                           new SeriesListFormatter(Series.Details.Full, false, true)),
                   Book.BKEY_SERIES_ARRAY, DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        fields.add(R.id.isbn, new TextViewAccessor<>(), DBDefinitions.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);

        fields.add(R.id.description, new TextViewAccessor<>(htmlFormatter),
                   DBDefinitions.KEY_DESCRIPTION);

        fields.add(R.id.genre, new TextViewAccessor<>(), DBDefinitions.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre);

        fields.add(R.id.language, new TextViewAccessor<>(languageFormatter),
                   DBDefinitions.KEY_LANGUAGE)
              .setRelatedFields(R.id.lbl_language);

        fields.add(R.id.pages, new TextViewAccessor<>(new PagesFormatter()),
                   DBDefinitions.KEY_PAGES);
        fields.add(R.id.format, new TextViewAccessor<>(), DBDefinitions.KEY_FORMAT);
        fields.add(R.id.color, new TextViewAccessor<>(), DBDefinitions.KEY_COLOR);
        fields.add(R.id.publisher, new TextViewAccessor<>(new CsvFormatter()),
                   Book.BKEY_PUBLISHER_ARRAY, DBDefinitions.KEY_PUBLISHER_NAME);

        fields.add(R.id.date_published, new TextViewAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_PUBLISHED)
              .setRelatedFields(R.id.lbl_date_published);

        fields.add(R.id.first_publication, new TextViewAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
              .setRelatedFields(R.id.lbl_first_publication);

        fields.add(R.id.print_run, new TextViewAccessor<>(), DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        fields.add(R.id.price_listed, new TextViewAccessor<>(moneyFormatter),
                   DBDefinitions.KEY_PRICE_LISTED)
              .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed);

        // Personal fields
        fields.add(R.id.bookshelves, new EntityListChipGroupAccessor(
                           () -> new ArrayList<>(mFragmentVM.getAllBookshelves()),
                           false), Book.BKEY_BOOKSHELF_ARRAY,
                   DBDefinitions.KEY_FK_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);

        fields.add(R.id.date_acquired, new TextViewAccessor<>(dateFormatter),
                   DBDefinitions.KEY_DATE_ACQUIRED)
              .setRelatedFields(R.id.lbl_date_acquired);

        fields.add(R.id.edition,
                   new BitmaskChipGroupAccessor(() -> Book.Edition.getEditions(context), false),
                   DBDefinitions.KEY_EDITION_BITMASK);

        fields.add(R.id.location, new TextViewAccessor<>(), DBDefinitions.KEY_LOCATION)
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        fields.add(R.id.rating, new RatingBarAccessor(), DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.add(R.id.condition, new TextViewAccessor<>(
                           new StringArrayResFormatter(context, R.array.conditions_book)),
                   DBDefinitions.KEY_BOOK_CONDITION)
              .setRelatedFields(R.id.lbl_condition);
        fields.add(R.id.condition_cover, new TextViewAccessor<>(
                           new StringArrayResFormatter(context, R.array.conditions_dust_cover)),
                   DBDefinitions.KEY_BOOK_CONDITION_COVER)
              .setRelatedFields(R.id.lbl_condition_cover);

        fields.add(R.id.notes, new TextViewAccessor<>(htmlFormatter),
                   DBDefinitions.KEY_PRIVATE_NOTES)
              .setRelatedFields(R.id.lbl_notes);

        fields.add(R.id.read_start, new TextViewAccessor<>(dateFormatter),
                   DBDefinitions.KEY_READ_START)
              .setRelatedFields(R.id.lbl_read_start);
        fields.add(R.id.read_end, new TextViewAccessor<>(dateFormatter), DBDefinitions.KEY_READ_END)
              .setRelatedFields(R.id.lbl_read_end);

        fields.add(R.id.icon_read, new BooleanIndicatorAccessor(), DBDefinitions.KEY_READ);

        fields.add(R.id.icon_signed, new BooleanIndicatorAccessor(), DBDefinitions.KEY_SIGNED)
              .setRelatedFields(R.id.lbl_signed);

        fields.add(R.id.price_paid, new TextViewAccessor<>(moneyFormatter),
                   DBDefinitions.KEY_PRICE_PAID)
              .setRelatedFields(R.id.price_paid_currency, R.id.lbl_price_paid);
    }

    @CallSuper
    @Override
    public void onResume() {
        // hook up the Views, and calls {@link #onPopulateViews}
        super.onResume();

        //noinspection ConstantConditions
        ((BookDetailsActivity) getActivity()).registerOnTouchListener(mOnTouchListener);
    }

    @Override
    @CallSuper
    public void onPause() {
        //noinspection ConstantConditions
        ((BookDetailsActivity) getActivity()).unregisterOnTouchListener(mOnTouchListener);

        super.onPause();
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case RequestCode.UPDATE_FIELDS_FROM_INTERNET:
            case RequestCode.BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // pass the data up
                        mBookViewModel.putResultData(data);
                    }
                    // onResume will display the changed book.
                    mBookViewModel.reload();
                }
                break;

            case RequestCode.BOOK_DUPLICATE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // pass the data up
                        mBookViewModel.putResultData(data);

                        long id = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                        if (id != 0) {
                            mBookViewModel.loadBook(id);
                        }
                    }
                    // onResume will display the new book
                    mBookViewModel.reload();
                    //TODO: swiping through the flattened booklist will not see
                    // the duplicated book until we go back to BoB.
                    // Easiest solution would be to remove the dup. option from this screen...
                }
                break;

            default: {
                // handle any cover image request codes
                final int cIdx = mFragmentVM.getAndClearCurrentCoverHandlerIndex();
                if (cIdx >= 0 && cIdx < mCoverHandler.length) {
                    if (mCoverHandler[cIdx] != null) {
                        if (mCoverHandler[cIdx].onActivityResult(requestCode, resultCode, data)) {
                            break;
                        }
                    } else {
                        // 2020-05-14: Can't explain it yet, but seen this to be null
                        // in the emulator:
                        // start device and app in normal portrait mode.
                        // turn the device twice CW, i.e. the screen should be upside down.
                        // The emulator will be upside down, but the app will be sideways.
                        // Take picture... get here and see NULL mCoverHandler[cIdx].

                        //noinspection ConstantConditions
                        Logger.warnWithStackTrace(getContext(), TAG,
                                                  "onActivityResult"
                                                  + "|mCoverHandler was NULL for cIdx=" + cIdx);
                    }
                }

                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    /** Called by the CoverHandler when a context menu is selected. */
    @Override
    public void setCurrentCoverIndex(@IntRange(from = 0) final int cIdx) {
        mFragmentVM.setCurrentCoverHandlerIndex(cIdx);
    }

    /**
     * At this point we're told to load our local (to the fragment) fields from the Book.
     *
     * <br><br>{@inheritDoc}
     *
     * @param fields to populate
     * @param book   to load
     */
    @Override
    protected void onPopulateViews(@NonNull final Fields fields,
                                   @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        //noinspection ConstantConditions
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            populateLendToField(mBookViewModel.getLoanee());
        }

        if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_TOC_BITMASK)) {
            populateToc(book);
        }

        if (DBDefinitions.isUsed(prefs, DBDefinitions.PREFS_IS_USED_THUMBNAIL)) {
            final Resources res = getResources();

            mCoverHandler[0] = new CoverHandler(
                    this, mProgressBar, book, mVb.isbn, 0, mVb.coverImage0,
                    res.getDimensionPixelSize(R.dimen.cover_details_0_height));

            mCoverHandler[1] = new CoverHandler(
                    this, mProgressBar, book, mVb.isbn, 1, mVb.coverImage1,
                    res.getDimensionPixelSize(R.dimen.cover_details_1_height));
        }

        // hide unwanted and empty fields
        //noinspection ConstantConditions
        fields.setVisibility(getView(), true, false);

        // Hide the Publication section label if none of the publishing fields are shown.
        setSectionVisibility(mVb.lblPublication,
                             mVb.publisher,
                             mVb.datePublished,
                             mVb.priceListed,
                             mVb.format,
                             mVb.color,
                             mVb.language,
                             mVb.pages);
        // Hide the "Personal notes" label if none of the notes fields are shown.
        setSectionVisibility(mVb.lblNotes,
                             mVb.notes,
                             mVb.iconSigned,
                             mVb.dateAcquired,
                             mVb.pricePaid,
                             mVb.readStart,
                             mVb.readEnd,
                             mVb.location);
    }

    /**
     * Inflates 'lend-to' field showing a person the book was lend to.
     * Allows returning the book via a context menu.
     *
     * <strong>Note:</strong> we pass in the loanee and handle visibility local as this
     * method can be called from anywhere.
     *
     * @param loanee the one who shall not be mentioned.
     */
    private void populateLendToField(@Nullable final String loanee) {
        if (loanee != null && !loanee.isEmpty()) {
            mVb.lendTo.setText(getString(R.string.lbl_loaned_to_name, loanee));
            mVb.lendTo.setVisibility(View.VISIBLE);
            mVb.lendTo.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                /** TODO: convert to MenuPicker context menu.... if I can be bothered. */
                @Override
                @CallSuper
                public void onCreateContextMenu(@NonNull final ContextMenu menu,
                                                @NonNull final View v,
                                                @NonNull
                                                final ContextMenu.ContextMenuInfo menuInfo) {
                    Resources r = getResources();
                    menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_DELETE,
                             r.getInteger(R.integer.MENU_ORDER_LENDING),
                             R.string.menu_loan_return_book);
                }
            });
        } else {
            mVb.lendTo.setVisibility(View.GONE);
            mVb.lendTo.setText("");
        }
    }

    /**
     * Show or hide the Table Of Content section.
     */
    private void populateToc(@NonNull final Book book) {
        final boolean isAnthology = book.isBitSet(DBDefinitions.KEY_TOC_BITMASK,
                                                  Book.TOC_MULTIPLE_WORKS);
        mVb.lblAnthology.setVisibility(isAnthology ? View.VISIBLE : View.GONE);
        mVb.iconAnthology.setVisibility(isAnthology ? View.VISIBLE : View.GONE);

        // we can get called more than once (when user moves sideways to another book),
        // so clear and hide/disable the view before populating it.
        // Actual visibility is handled after building the list.
        mVb.toc.removeAllViews();
        mVb.toc.setVisibility(View.GONE);
        mVb.btnShowToc.setChecked(false);

        final ArrayList<TocEntry> tocList = book.getParcelableArrayList(Book.BKEY_TOC_ARRAY);

        if (!tocList.isEmpty()) {

            final Context context = getContext();

            for (TocEntry tocEntry : tocList) {
                final RowTocEntryWithAuthorBinding rowVb = RowTocEntryWithAuthorBinding
                        .inflate(getLayoutInflater(), mVb.toc, false);

                //noinspection ConstantConditions
                rowVb.title.setText(tocEntry.getLabel(context));
                rowVb.author.setText(tocEntry.getPrimaryAuthor().getLabel(context));

                final boolean isSet = tocEntry.getBookCount() > 1;
                if (isSet) {
                    rowVb.cbxMultipleBooks.setVisibility(View.VISIBLE);
                    rowVb.cbxMultipleBooks.setOnClickListener(v -> {
                        final String titles =
                                Csv.textList(context, mFragmentVM.getBookTitles(tocEntry),
                                             element -> element.second);
                        StandardDialogs.infoPopup(rowVb.cbxMultipleBooks, titles);
                    });
                }

                final String date = tocEntry.getFirstPublication();
                // "< 4" covers empty and illegal dates
                if (date.length() < 4) {
                    rowVb.year.setVisibility(View.GONE);
                } else {
                    rowVb.year.setVisibility(View.VISIBLE);
                    // show full date string (if available)
                    rowVb.year.setText(context.getString(R.string.brackets, date));
                }
                mVb.toc.addView(rowVb.getRoot());
            }

            mVb.lblToc.setVisibility(View.VISIBLE);
            mVb.btnShowToc.setVisibility(View.VISIBLE);

        } else {
            mVb.lblToc.setVisibility(View.GONE);
            mVb.btnShowToc.setVisibility(View.GONE);
        }
    }

    /**
     * If all field Views are View.GONE, set the section View to View.GONE as well.
     * Otherwise, set the section View to View.VISIBLE.
     *
     * @param sectionView field to set
     * @param fieldViews  to check
     */
    private void setSectionVisibility(@NonNull final View sectionView,
                                      @NonNull final View... fieldViews) {
        for (View view : fieldViews) {
            if (view != null && view.getVisibility() != View.GONE) {
                // at least one field was visible
                sectionView.setVisibility(View.VISIBLE);
                return;
            }
        }
        // all fields were gone.
        sectionView.setVisibility(View.GONE);
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        // add the search action view
        inflater.inflate(R.menu.sav_search, menu);
        //noinspection ConstantConditions
        MenuHandler.setupSearch(getActivity(), menu);

        inflater.inflate(R.menu.book, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        final Book book = mBookViewModel.getBook();

        //noinspection ConstantConditions
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        final boolean isSaved = !book.isNew();
        final boolean isRead = book.getBoolean(DBDefinitions.KEY_READ);
        final boolean isAvailable = mBookViewModel.isAvailable();

        menu.findItem(R.id.MENU_BOOK_READ).setVisible(isSaved && !isRead);
        menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isSaved && isRead);

        //TODO: swiping through the flattened booklist will not see
        // the duplicated book until we go back to BoB.
        // Temporary solution is removing the 'duplicate' option from this screen...
        menu.findItem(R.id.MENU_BOOK_DUPLICATE).setVisible(false);

        menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
            .setVisible(GoodreadsManager.isShowSyncMenus(prefs));

        // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
        final boolean useLending = DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE);
        menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isSaved && isAvailable);
        menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && isSaved && !isAvailable);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final Book book = mBookViewModel.getBook();

        switch (item.getItemId()) {
            case R.id.MENU_BOOK_EDIT: {
                startEditBook();
                return true;
            }
            case R.id.MENU_BOOK_DELETE: {
                final String title = book.getString(DBDefinitions.KEY_TITLE);
                final List<Author> authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
                //noinspection ConstantConditions
                StandardDialogs.deleteBook(getContext(), title, authors, () -> {
                    mBookViewModel.deleteBook(getContext());

                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mBookViewModel.getResultIntent());
                    getActivity().finish();
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                final Intent dupIntent = new Intent(getContext(), EditBookActivity.class)
                        .putExtra(Book.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(dupIntent, RequestCode.BOOK_DUPLICATE);
                return true;
            }
            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle 'read' status of the book
                final boolean value = mBookViewModel.toggleRead();
                final Field<Boolean, View> field = getField(R.id.icon_read);
                field.getAccessor().setValue(value);
                // Still call this, as it will handle related views (none for now, but future-proof)
                //noinspection ConstantConditions
                field.setVisibility(getView(), true, false);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_ADD: {
                EditLenderDialogFragment
                        .newInstance(RK_EDIT_LENDER, book)
                        .show(getChildFragmentManager(), EditLenderDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                mBookViewModel.deleteLoan();
                populateLendToField(null);
                return true;
            }
            case R.id.MENU_SHARE: {
                //noinspection ConstantConditions
                startActivity(book.getShareIntent(getContext()));
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
                mGrSendOneBookTask.startTask(book.getId());
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startEditBook() {
        final Intent editIntent = new Intent(getContext(), EditBookActivity.class)
                .putExtra(DBDefinitions.KEY_PK_ID, mBookViewModel.getBook().getId());
        startActivityForResult(editIntent, RequestCode.BOOK_EDIT);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem menuItem) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_LOAN_DELETE:
                mBookViewModel.deleteLoan();
                populateLendToField(null);
                return true;

            default:
                return super.onContextItemSelected(menuItem);
        }
    }

    /**
     * Listener to handle 'fling' events to move to the next/previous book.
     */
    class FlingHandler
            extends GestureDetector.SimpleOnGestureListener {

        private static final float SENSITIVITY = 100;

        @Override
        public boolean onFling(@Nullable final MotionEvent e1,
                               @Nullable final MotionEvent e2,
                               final float velocityX,
                               final float velocityY) {

            if (e1 == null || e2 == null) {
                return false;
            }

            // make sure we're not getting a false-positive due to the user
            // swiping to open the navigation drawer.
            //noinspection ConstantConditions
            if (((BaseActivity) getActivity()).isNavigationDrawerVisible()) {
                return false;
            }

            if ((e1.getX() - e2.getX()) > SENSITIVITY) {
                if (mFragmentVM.move(mBookViewModel.getBook(), true)) {
                    populateViews();
                    return true;
                }
            } else if ((e2.getX() - e1.getX()) > SENSITIVITY) {
                if (mFragmentVM.move(mBookViewModel.getBook(), false)) {
                    populateViews();
                    return true;
                }
            }

            return false;
        }

        /**
         * <a href="https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures">
         * detect-a-subset-of-supported-gestures</a>
         * <p>
         * ... implement an onDown() method that returns true. This is because all gestures
         * begin with an onDown() message. If you return false from onDown(),
         * as GestureDetector.SimpleOnGestureListener does by default, the system assumes that
         * you want to ignore the rest of the gesture, and the other methods of
         * GestureDetector.OnGestureListener never get called...
         *
         * @param e The down motion event.
         *
         * @return {@code true}
         */
        @Override
        public boolean onDown(@NonNull final MotionEvent e) {
            return true;
        }
    }
}
