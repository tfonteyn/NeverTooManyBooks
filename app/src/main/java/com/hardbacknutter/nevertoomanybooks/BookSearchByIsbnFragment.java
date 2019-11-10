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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.EditIsbn;

/**
 * <strong>Notes on the virtual keyboard:</strong>
 * <p>
 * Stop if from showing when a field gets the focus.<br>
 * This must be done for <strong>ALL</strong> fields individually
 * <pre>
 * {@code
 *      editText.setShowSoftInputOnFocus(false);
 * }
 * </pre>
 * Hide it when already showing:
 * <pre>
 * {@code
 *      InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
 *      if (imm != null && imm.isActive(this)) {
 *          imm.hideSoftInputFromWindow(getWindowToken(), 0);
 *      }
 * }
 * </pre>
 */
public class BookSearchByIsbnFragment
        extends BookSearchBaseFragment {

    public static final String TAG = "BookSearchByIsbnFragment";

    /** option to start in scan mode (versus manual entry). */
    static final String BKEY_IS_SCAN_MODE = TAG + ":isScanMode";

    /** wait for the scanner; milliseconds. */
    private static final long SCANNER_WAIT = 1_000;
    /** repeat waiting for the scanner 3 times before reporting failure. */
    private static final int SCANNER_RETRY = 3;
    /**
     * Flag to indicate the Activity should not call 'finish()' after a failure.
     */
    private boolean mKeepAlive;

    /** Flag to allow ASIN key input (true) or pure ISBN input (false). */
    private boolean mAllowAsin;

    private boolean mScanModeHasUI;

    /** User input field. */
    @Nullable
    private EditIsbn mIsbnView;
    @Nullable
    private TextView mAltIsbnView;
    /** The scanner. */
    @Nullable
    private ScannerViewModel mScannerModel;
    /** process search results. */
    private final SearchCoordinator.SearchFinishedListener mSearchFinishedListener =
            new SearchCoordinator.SearchFinishedListener() {
                @Override
                public void onSearchFinished(final boolean wasCancelled,
                                             @NonNull final Bundle bookData) {
                    try {
                        if (wasCancelled) {
                            // the search was cancelled, resume scanning
                            if (isScanMode()) {
                                startScannerActivity();
                            }
                        } else {
                            // A non-empty result will have a title or at least 3 fields.
                            // The isbn field will always be present as we searched on one.
                            // The title field, *might* be there but *might* be empty.
                            // So a valid result means we either need a title, or a
                            // third field.
                            String title = bookData.getString(DBDefinitions.KEY_TITLE);
                            if ((title != null && !title.isEmpty())
                                || bookData.size() > 2) {
                                Intent intent = new Intent(getContext(), EditBookActivity.class)
                                        .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);

                                // Clear the data entry fields, ready for the next one.
                                if (mIsbnView != null) {
                                    mIsbnView.setText("");
                                }
                                mBookSearchBaseModel.clearSearchText();

                            } else {
                                //noinspection ConstantConditions
                                UserMessage.show(getActivity(),
                                                 R.string.warning_no_matching_book_found);
                            }
                        }
                    } finally {
                        mBookSearchBaseModel.setSearchCoordinator(0);
                        // Tell our listener they can close the progress dialog.
                        mTaskManager.sendHeaderUpdate(null);
                    }
                }
            };
    @Nullable
    private ImageButton mAltBtnView;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mandatory
        setHasOptionsMenu(true);

        //TODO: not sure we'll keep this... for now enabled via the debug menu only
        //noinspection ConstantConditions
        mScanModeHasUI = PreferenceManager.getDefaultSharedPreferences(getContext())
                                          .getBoolean(Prefs.pk_scanner_has_ui, false);

        // Have we been started in UI or in scan mode.
        Bundle args = getArguments();
        if (args != null && args.getBoolean(BookSearchByIsbnFragment.BKEY_IS_SCAN_MODE)) {
            mScannerModel = new ViewModelProvider(this).get(ScannerViewModel.class);
        }
    }

    /**
     * Convenience/readability method.
     *
     * @return flag
     */
    private boolean isScanMode() {
        return mScannerModel != null;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        if (isScanMode() && !mScanModeHasUI) {
            return null;
        }

        View view = inflater.inflate(R.layout.fragment_booksearch_by_isbn, container, false);
        mIsbnView = view.findViewById(R.id.isbn);
        mAltIsbnView = view.findViewById(R.id.altIsbn);
        mAltBtnView = view.findViewById(R.id.btn_swap);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mIsbnView != null) {
            // setup the keypad if we have a UI.
            initUI();
            if (savedInstanceState == null) {
                //FIXME: find a solution for showing reg dialog AND starting a scan
                //noinspection ConstantConditions
                SearchSites.promptToRegister(getContext(), "search",
                                             mBookSearchBaseModel.getEnabledSearchSites());
            }
        }

        // if we already have an isbn from somewhere, auto-start a search
        String isbn = mBookSearchBaseModel.getIsbnSearchText();
        if (!isbn.isEmpty()) {
            if (mIsbnView != null) {
                mIsbnView.setText(isbn);
            }
            prepareSearch(isbn);
            return;
        }

        // scan mode ?
        if (isScanMode()) {
            if (mScannerModel.isFirstStart()) {
                startScannerActivity();
            }
        }
    }

    /**
     * Setup the UI.
     */
    private void initUI() {
        // sanity check
        Objects.requireNonNull(mIsbnView);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.title_search_isbn);

        View view = getView();

        //noinspection ConstantConditions
        view.findViewById(R.id.key_0).setOnClickListener(v -> mIsbnView.onKey("0"));
        view.findViewById(R.id.key_1).setOnClickListener(v -> mIsbnView.onKey("1"));
        view.findViewById(R.id.key_2).setOnClickListener(v -> mIsbnView.onKey("2"));
        view.findViewById(R.id.key_3).setOnClickListener(v -> mIsbnView.onKey("3"));
        view.findViewById(R.id.key_4).setOnClickListener(v -> mIsbnView.onKey("4"));
        view.findViewById(R.id.key_5).setOnClickListener(v -> mIsbnView.onKey("5"));
        view.findViewById(R.id.key_6).setOnClickListener(v -> mIsbnView.onKey("6"));
        view.findViewById(R.id.key_7).setOnClickListener(v -> mIsbnView.onKey("7"));
        view.findViewById(R.id.key_8).setOnClickListener(v -> mIsbnView.onKey("8"));
        view.findViewById(R.id.key_9).setOnClickListener(v -> mIsbnView.onKey("9"));
        view.findViewById(R.id.key_X).setOnClickListener(v -> mIsbnView.onKey("X"));

        Button delBtn = view.findViewById(R.id.isbn_del);

        delBtn.setOnClickListener(v -> mIsbnView.onKey(KeyEvent.KEYCODE_DEL));
        delBtn.setOnLongClickListener(v -> {
            mIsbnView.setText("");
            //noinspection ConstantConditions
            mAltIsbnView.setText("");
            return true;
        });

        // allow a click on either the icon or the text view to swap numbers
        //noinspection ConstantConditions
        mAltIsbnView.setOnClickListener(this::onSwapNumbers);
        //noinspection ConstantConditions
        mAltBtnView.setOnClickListener(this::onSwapNumbers);

        // auto update the alternative ISBN number.
        mIsbnView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s,
                                          final int start,
                                          final int count,
                                          final int after) {

            }

            @Override
            public void onTextChanged(final CharSequence s,
                                      final int start,
                                      final int before,
                                      final int count) {

            }

            @Override
            public void afterTextChanged(final Editable s) {
                String isbn = s.toString();
                int len = isbn.length();
                if (len == 10 || len == 13) {
                    String altIsbn = ISBN.isbn2isbn(isbn);
                    if (!altIsbn.equals(isbn)) {
                        mAltIsbnView.setText(altIsbn);
                        mAltIsbnView.setVisibility(View.VISIBLE);
                        mAltBtnView.setVisibility(View.VISIBLE);
                    }
                } else {
                    mAltIsbnView.setVisibility(View.INVISIBLE);
                    mAltBtnView.setVisibility(View.INVISIBLE);
                }
            }
        });

        view.findViewById(R.id.btn_search).setOnClickListener(v -> {
            //noinspection ConstantConditions
            String isbn = mIsbnView.getText().toString().trim();
            mBookSearchBaseModel.setIsbnSearchText(isbn);
            prepareSearch(isbn);
        });

        // init the isbn edit field if needed (avoid initializing twice)
        if (mAllowAsin) {
            //noinspection ConstantConditions
            mIsbnView.setAllowAsin(mAllowAsin);
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        //dev note: we could eliminate onPrepareOptionsMenu as invalidateOptionsMenu()
        // MUST be called to make this menu be show for as long there is only this one
        // option in the menu. But... leaving the code as-is, so if/when a second menu
        // item is added, no code changes are needed.
        menu.add(Menu.NONE, R.id.MENU_PREFS_ASIN, 0, R.string.lbl_allow_asin)
            .setCheckable(true)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        // if Amazon is enabled, we show the ASIN option; else make sure it's disabled.
        boolean amazon = (mBookSearchBaseModel.getEnabledSearchSites()
                          & SearchSites.AMAZON) != 0;
        MenuItem asin = menu.findItem(R.id.MENU_PREFS_ASIN);
        asin.setVisible(amazon);
        if (!amazon) {
            asin.setChecked(false);
        }

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_ASIN:
                mAllowAsin = !item.isChecked();

                item.setChecked(mAllowAsin);
                //noinspection ConstantConditions
                mIsbnView.setAllowAsin(mAllowAsin);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    SearchCoordinator.SearchFinishedListener getSearchFinishedListener() {
        return mSearchFinishedListener;
    }

    /**
     * Search with ISBN.
     * <p>
     * mIsbnSearchText must be 10 characters (or more) to even consider a search.
     */
    private void prepareSearch(@NonNull String isbn) {
        // sanity check
        if (isbn.length() < 10) {
            return;
        }

        // intercept UPC numbers
        isbn = ISBN.upc2isbn(isbn);
        if (isbn.length() < 10) {
            return;
        }

        // not a valid ISBN/ASIN ?
        if (!ISBN.isValid(isbn) && (!mAllowAsin || !ISBN.isValidAsin(isbn))) {
            isbnInvalid(isbn);
            return;
        }
        // at this point, we have a valid isbn/asin.
        if (isScanMode()) {
            //noinspection ConstantConditions
            mScannerModel.onValidBeep(getContext());
        }

        // See if ISBN already exists in our database, if not then start the search.
        final long existingId = mBookSearchBaseModel.getDb().getBookIdFromIsbn(isbn, true);
        if (existingId != 0) {
            isbnAlreadyPresent(existingId);
        } else {
            startSearch();
        }
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * Or restart the scanner if applicable.
     *
     * @return {@code true} if search was started.
     */
    boolean startSearch() {
        if (!super.startSearch()) {
            //TEST: should we actually restart the scanner here ?
            if (isScanMode()) {
                // we didn't have an isbn, but we're scanning. Restart scanner.
                startScannerActivity();
            }
        }
        return true;
    }

    /**
     * ISBN was already present, Verify what the user wants to do.
     *
     * @param existingId of the book we already have in the database
     */
    private void isbnAlreadyPresent(final long existingId) {
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_duplicate_book)
                .setMessage(R.string.confirm_duplicate_book_message)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                // User aborts this isbn
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // reset the now-discarded details
                    mBookSearchBaseModel.clearSearchText();
                    if (isScanMode()) {
                        startScannerActivity();
                    }
                })
                // User wants to review the existing book
                .setNeutralButton(R.string.edit, (dialog, which) -> {
                    Intent intent = new Intent(getContext(), EditBookActivity.class)
                            .putExtra(DBDefinitions.KEY_PK_ID, existingId);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                })
                // User wants to add regardless
                .setPositiveButton(R.string.btn_confirm_add, (dialog, which) -> startSearch())
                .create()
                .show();
    }

    /**
     * ISBN was invalid. Inform the user and go back to either the UI or the scanner.
     *
     * @param isbn the isbn which was invalid
     */
    private void isbnInvalid(@NonNull final String isbn) {
        String msg;
        if (mAllowAsin) {
            msg = getString(R.string.warning_x_is_not_a_valid_isbn_or_asin, isbn);
        } else {
            msg = getString(R.string.warning_x_is_not_a_valid_isbn, isbn);
        }

        if (isScanMode()) {
            //noinspection ConstantConditions
            mScannerModel.onInvalidBeep(getContext());

            //noinspection ConstantConditions
            UserMessage.show(getActivity(), msg);
            // reset the now-discarded details
            mBookSearchBaseModel.clearSearchText();

            startScannerActivity();
        } else {
            //noinspection ConstantConditions
            UserMessage.show(mIsbnView, msg);
        }
    }

    /**
     * Start scanner activity.
     */
    private void startScannerActivity() {
        //noinspection ConstantConditions
        Scanner scanner = mScannerModel.getScanner();
        try {
            if (scanner == null) {
                scanner = ScannerManager.getScanner(mHostActivity);
                if (scanner == null) {
                    noScanner();
                    return;
                }
                mScannerModel.setScanner(scanner);
            }

            // this is really for the Google barcode library which is loaded on first access.
            // We're very conservative/paranoid here, as we already triggered a load during startup.
            int retry = SCANNER_RETRY;
            while (!scanner.isOperational() && retry > 0) {
                //noinspection ConstantConditions
                UserMessage.show(getActivity(), R.string.info_waiting_for_scanner);

                try {
                    Thread.sleep(SCANNER_WAIT);
                } catch (@NonNull final InterruptedException ignore) {
                }
                if (scanner.isOperational()) {
                    break;
                }
                retry--;
            }

            // ready or not, go scan
            if (!mScannerModel.isScannerStarted()) {
                if (!scanner.startActivityForResult(this, UniqueId.REQ_SCAN_BARCODE)) {
                    // the scanner was loaded, but (still) refuses to start.
                    scannerStartFailed();
                    return;
                }
                mScannerModel.setScannerStarted(true);
            }

        } catch (@NonNull final RuntimeException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e);
            noScanner();
        }
    }

    private void scannerStartFailed() {
        String msg = getString(R.string.warning_scanner_failed_to_start)
                     + "\n\n"
                     + getString(R.string.warning_scanner_retry_install);

        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setNeutralButton(R.string.lbl_settings, (d, w) -> {
                    Intent intent = new Intent(mHostActivity, SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.psk_barcode_scanner);

                    mHostActivity.startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                })
                .setPositiveButton(R.string.retry, (d, w) -> {
                    // yes, nothing to do. Closing this dialog should bring up the scanner again
                })
                .create()
                .show();
        mKeepAlive = true;
    }

    /**
     * None of the scanners was available or working correctly.
     * Tell the user, and take them to the preferences to select a scanner.
     */
    private void noScanner() {
        String msg = getString(R.string.info_bad_scanner) + '\n'
                     + getString(R.string.info_install_scanner_recommendation);
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setOnDismissListener(d -> {
                    Intent intent = new Intent(mHostActivity, SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.psk_barcode_scanner);

                    mHostActivity.startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                })
                .create()
                .show();
        mKeepAlive = true;
    }

    /**
     * Scanner Cancelled/failed.
     * Pass the last book we got back to our caller and finish here.
     */
    private void scanFailedOrCancelled() {
        Intent resultData = mBookSearchBaseModel.getActivityResultData();
        if (resultData.getExtras() != null) {
            mHostActivity.setResult(Activity.RESULT_OK, resultData);
        }

        // and exit if no dialog present.
        if (!mKeepAlive) {
            mHostActivity.finish();
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case UniqueId.REQ_SCAN_BARCODE: {
                //noinspection ConstantConditions
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {

                    if (BuildConfig.DEBUG) {
                        // detect emulator for testing
                        if (Build.PRODUCT.startsWith("sdk")) {
                            // when used, the file must be in the root external app dir.
                            //noinspection ConstantConditions
                            File file = new File(StorageUtils.getRootDir(getContext()),
                                                 "barcode.jpg");
                            if (file.exists()) {
                                Bitmap dummy = BitmapFactory.decodeFile(file.getAbsolutePath());
                                if (data != null
                                    && data.getExtras() != null
                                    && data.getExtras().containsKey("data")) {
                                    data.putExtra("data", dummy);
                                } else {
                                    try {
                                        StorageUtils.copyFile(file, CameraHelper.getDefaultFile());
                                    } catch (@NonNull final IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                    Scanner scanner = mScannerModel.getScanner();
                    //noinspection ConstantConditions
                    String barCode = scanner.getBarcode(data);
                    if (barCode != null) {
                        mBookSearchBaseModel.setIsbnSearchText(barCode);

                        if (mScanModeHasUI && mIsbnView != null) {
                            mIsbnView.setText(barCode);
                            // now wait for user to hit search button.
                        } else {
                            // no UI, auto-start search
                            prepareSearch(barCode);
                        }
                        return;
                    }
                }

                scanFailedOrCancelled();
                return;
            }
            case UniqueId.REQ_BOOK_EDIT: {
                // first do the common action when the user has saved the data for the book.
                super.onActivityResult(requestCode, resultCode, data);

                if (isScanMode()) {
                    // go scan next book until the user cancels scanning.
                    startScannerActivity();
                }
                break;
            }
            case UniqueId.REQ_NAV_PANEL_SETTINGS: {
                if (isScanMode()) {
                    // Make sure the scanner gets re-initialised.
                    mScannerModel.setScanner(null);
                    startScannerActivity();
                }
                break;
            }
            case UniqueId.REQ_UPDATE_GOOGLE_PLAY_SERVICES: {
                if (resultCode == Activity.RESULT_OK) {
                    if (isScanMode()) {
                        // go scan next book until the user cancels scanning.
                        startScannerActivity();
                    }
                } else {
                    scanFailedOrCancelled();
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mIsbnView != null) {
            //noinspection ConstantConditions
            mBookSearchBaseModel.setIsbnSearchText(mIsbnView.getText().toString().trim());
        }
    }

    /**
     * Swap the content of ISBN and alternative-ISBN texts.
     *
     * @param view that was clicked on
     */
    private void onSwapNumbers(@SuppressWarnings("unused") @NonNull final View view) {
        //noinspection ConstantConditions
        String isbn = mIsbnView.getText().toString().trim();
        //noinspection ConstantConditions
        String altIsbn = mAltIsbnView.getText().toString().trim();
        mIsbnView.setText(altIsbn);
        mAltIsbnView.setTag(isbn);
    }
}
