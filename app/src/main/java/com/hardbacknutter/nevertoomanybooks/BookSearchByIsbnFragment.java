/*
 * @Copyright 2020 HardBackNutter
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
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.PermissionsHelper;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.AltIsbnTextWatcher;
import com.hardbacknutter.nevertoomanybooks.widgets.EditIsbn;
import com.hardbacknutter.nevertoomanybooks.widgets.IsbnValidationTextWatcher;

/**
 * The input field is not being limited in length. This is to allow entering UPC_A numbers.
 */
public class BookSearchByIsbnFragment
        extends BookSearchBaseFragment
        implements PermissionsHelper.RequestHandler {

    /** Log tag. */
    public static final String TAG = "BookSearchByIsbnFrag";

    static final String BKEY_SCAN_MODE = TAG + ":scanMode";

    /** User input field. */
    @Nullable
    private EditIsbn mIsbnView;

    @Nullable
    private Button mAltIsbnButton;

    private boolean mScanMode;

    /** The scanner. */
    @Nullable
    private ScannerViewModel mScannerModel;

    /** manage the validation check next to the field. */
    private IsbnValidationTextWatcher mIsbnValidationTextWatcher;

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        // Camera permissions
        onRequestPermissionsResultCallback(requestCode, permissions, grantResults);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.fragment_booksearch_by_isbn, container, false);
        mIsbnView = view.findViewById(R.id.isbn);
        mAltIsbnButton = view.findViewById(R.id.btn_altIsbn);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            mScanMode = args.getBoolean(BKEY_SCAN_MODE, false);
        }
        //noinspection ConstantConditions
        mScannerModel = new ViewModelProvider(getActivity()).get(ScannerViewModel.class);

        getActivity().setTitle(R.string.title_search_isbn);

        final View view = getView();
        // stop lint being very annoying...
        Objects.requireNonNull(mIsbnView, "mIsbnView");
        Objects.requireNonNull(mAltIsbnButton, "mAltIsbnButton");

        // copyModel2View();
        mIsbnView.setText(mSearchCoordinator.getIsbnSearchText());

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

        final Button delBtn = view.findViewById(R.id.isbn_del);
        delBtn.setOnClickListener(v -> mIsbnView.onKey(KeyEvent.KEYCODE_DEL));
        delBtn.setOnLongClickListener(v -> {
            mIsbnView.setText("");
            return true;
        });

        mIsbnValidationTextWatcher = new IsbnValidationTextWatcher(
                mIsbnView, mSearchCoordinator.isStrictIsbn());
        mIsbnView.addTextChangedListener(mIsbnValidationTextWatcher);
        mIsbnView.addTextChangedListener(new AltIsbnTextWatcher(mIsbnView, mAltIsbnButton));

        //noinspection ConstantConditions
        view.findViewById(R.id.btn_search)
            .setOnClickListener(v -> prepareSearch(mIsbnView.getText().toString().trim()));

        // auto-start scanner first time.
        if (mScanMode && mScannerModel.isFirstStart()) {
            mScanMode = mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
        }

//        if (savedInstanceState == null) {
//            mSearchCoordinator.getSiteList().promptToRegister(getContext(), false, "search");
//        }

//        Configuration c = getActivity().getResources().getConfiguration();
//        Log.d(TAG,"CONFIG: " + c);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_SCAN_BARCODE, 0, R.string.btn_scan_barcode)
            .setIcon(R.drawable.ic_barcode)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.MENU_STRICT_ISBN, 0, R.string.menu_strict_isbn)
            .setCheckable(true)
            .setChecked(mSearchCoordinator.isStrictIsbn())
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_SCAN_BARCODE: {
                Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                mScanMode = mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
                return true;
            }
            case R.id.MENU_STRICT_ISBN: {
                final boolean checked = !item.isChecked();
                item.setChecked(checked);
                mIsbnValidationTextWatcher.setStrictIsbn(checked);
                mSearchCoordinator.setStrictIsbn(checked);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //noinspection ConstantConditions
        mSearchCoordinator.setIsbnSearchText(mIsbnView.getText().toString().trim());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_SCAN_MODE, mScanMode);
    }

    @Override
    void onSearchCancelled() {
        super.onSearchCancelled();

        if (mScanMode) {
            Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
            mScanMode = mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
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
            case UniqueId.REQ_BOOK_EDIT: {
                // first do the common action when the user has saved the data for the book.
                super.onActivityResult(requestCode, resultCode, data);
                // go scan next book until the user cancels scanning.
                if (mScanMode) {
                    Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                    mScanMode = mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
                }
                break;
            }
            case UniqueId.REQ_SCAN_BARCODE: {
                Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {
                    if (BuildConfig.DEBUG) {
                        //noinspection ConstantConditions
                        mScannerModel.fakeBarcodeScan(getContext(), data);
                    }

                    //noinspection ConstantConditions
                    final String barCode = mScannerModel.getScanner()
                                                        .getBarcode(getContext(), data);
                    if (barCode != null) {
                        //noinspection ConstantConditions
                        mIsbnView.setText(barCode);
                        prepareSearch(barCode);
                        return;
                    }
                }

                mScanMode = false;
                return;
            }
            case UniqueId.REQ_SETTINGS: {
                // Settings initiated from the local menu or dialog box.
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // update the search sites list.
                    final SiteList siteList =
                            data.getParcelableExtra(SiteList.Type.Data.getBundleKey());
                    if (siteList != null) {
                        final SearchCoordinator model =
                                new ViewModelProvider(this).get(SearchCoordinator.class);
                        model.setSiteList(siteList);
                    }

                    // init the scanner if it was changed.
                    if (data.getBooleanExtra(UniqueId.BKEY_SHOULD_INIT_SCANNER, false)) {
                        Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                        mScannerModel.resetScanner();
                    }
                }

                if (mScanMode) {
                    Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                    mScanMode = mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
                }
                break;
            }
            case UniqueId.REQ_UPDATE_GOOGLE_PLAY_SERVICES: {
                if (mScanMode) {
                    if (resultCode == Activity.RESULT_OK) {
                        Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                        // go scan next book until the user cancels scanning.
                        mScanMode = mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
                    } else {
                        mScanMode = false;
                    }
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
    void clearPreviousSearchCriteria() {
        super.clearPreviousSearchCriteria();
        //noinspection ConstantConditions
        mIsbnView.setText("");
    }

    /**
     * Search with ISBN.
     * <p>
     *
     * @param userEntry isbn text to search for.
     *                  Must be 10 characters (or more) to even consider a search.
     */
    private void prepareSearch(@NonNull final String userEntry) {

        final boolean strictIsbn = mSearchCoordinator.isStrictIsbn();
        final ISBN code = new ISBN(userEntry, strictIsbn);

        // not a valid code ?
        if (!code.isValid(strictIsbn)) {
            if (mScanMode) {
                Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                //noinspection ConstantConditions
                mScannerModel.onInvalidBeep(getContext());
            }

            //noinspection ConstantConditions
            Snackbar.make(mIsbnView,
                          getString(R.string.warning_x_is_not_a_valid_code, userEntry),
                          Snackbar.LENGTH_LONG).show();

            if (mScanMode) {
                Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                mScanMode = mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
            }
            return;
        }

        // at this point, we know we have a searchable code
        mSearchCoordinator.setIsbnSearchText(code.asText());

        if (strictIsbn && mScanMode) {
            Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
            //noinspection ConstantConditions
            mScannerModel.onValidBeep(getContext());
        }

        // See if ISBN already exists in our database, if not then start the search.
        final long existingId = mDb.getBookIdFromIsbn(code);
        if (existingId == 0) {
            startSearch();
        } else {
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.title_duplicate_book)
                    .setMessage(R.string.confirm_duplicate_book_message)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // User aborts this isbn
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        clearPreviousSearchCriteria();
                        if (mScanMode) {
                            Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                            mScanMode = mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
                        }
                    })
                    // User wants to review the existing book
                    .setNeutralButton(R.string.edit, (dialog, which) -> {
                        final Intent intent = new Intent(getContext(), EditBookActivity.class)
                                .putExtra(DBDefinitions.KEY_PK_ID, existingId);
                        startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                    })
                    // User wants to add regardless
                    .setPositiveButton(R.string.btn_confirm_add, (dialog, which) -> startSearch())
                    .create()
                    .show();
        }
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // A non-empty result will have a title, or at least 3 fields:
        // The isbn field will always be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = bookData.getString(DBDefinitions.KEY_TITLE);
        if ((title != null && !title.isEmpty()) || bookData.size() > 2) {
            final Intent intent = new Intent(getContext(), EditBookActivity.class)
                    .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
            startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
            clearPreviousSearchCriteria();
        } else {
            //noinspection ConstantConditions
            Snackbar.make(mIsbnView, R.string.warning_no_matching_book_found,
                          Snackbar.LENGTH_LONG).show();
        }
    }

}
