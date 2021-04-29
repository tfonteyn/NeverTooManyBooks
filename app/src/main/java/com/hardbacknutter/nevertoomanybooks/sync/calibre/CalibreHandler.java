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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.Menu;
import android.view.View;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * A delegate class for handling a Calibre enabled Book.
 */
public class CalibreHandler {

    /** Log tag. */
    private static final String TAG = "CalibreHandler";

    /** The Calibre API object. */
    @NonNull
    private final CalibreContentServer mServer;

    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> mPickFolderLauncher;
    /** ONLY USED AND VALID WHILE RUNNING THE {@link #mPickFolderLauncher}. */
    @Nullable
    private Book mTempBookWhileRunningPickFolder;

    private Activity mActivity;

    /** The host view; used for context, resources, Snackbar. */
    private View mView;

    @Nullable
    private ProgressDelegate mProgressDelegate;

    private CalibreHandlerViewModel mVm;

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @throws CertificateException on failures related to a user installed CA.
     * @throws SSLException         on secure connection failures
     */
    public CalibreHandler(@NonNull final Context context)
            throws CertificateException, SSLException {
        mServer = new CalibreContentServer(context);
    }

    /**
     * Initializer for use from within an Activity.
     *
     * @param activity the hosting Activity
     * @param view     the root view of the Activity (e.g. mVb.getRoot())
     */
    public void onViewCreated(@NonNull final FragmentActivity activity,
                              @NonNull final View view) {
        onViewCreated(activity, view,
                      activity, activity,
                      activity);
    }

    /**
     * Initializer for use from within an Fragment.
     *
     * @param fragment the hosting Fragment
     */
    public void onViewCreated(@NonNull final Fragment fragment) {
        //noinspection ConstantConditions
        onViewCreated(fragment.getActivity(), fragment.getView(),
                      fragment, fragment.getViewLifecycleOwner(),
                      fragment);
    }

    /**
     * Host (Fragment/Activity) independent initializer.
     *
     * @param activity the hosting Activity
     * @param view     the hosting component root view
     */
    private void onViewCreated(@NonNull final Activity activity,
                               @NonNull final View view,
                               @NonNull final ViewModelStoreOwner viewModelStoreOwner,
                               @NonNull final LifecycleOwner lifecycleOwner,
                               @NonNull final ActivityResultCaller caller) {
        mActivity = activity;
        mView = view;

        mPickFolderLauncher = caller.registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), uri -> {
                    if (uri != null) {
                        CalibreContentServer.setFolderUri(mView.getContext(), uri);
                        Objects.requireNonNull(mTempBookWhileRunningPickFolder,
                                               "mTempBookWhileRunningPickFolder");
                        final Book book = mTempBookWhileRunningPickFolder;
                        mTempBookWhileRunningPickFolder = null;
                        download(book, uri);
                    }
                });

        mVm = new ViewModelProvider(viewModelStoreOwner).get(CalibreHandlerViewModel.class);
        mVm.init(mServer);
        mVm.onFinished().observe(lifecycleOwner, this::onFinished);
        mVm.onCancelled().observe(lifecycleOwner, this::onCancelled);
        mVm.onFailure().observe(lifecycleOwner, this::onFailure);
        mVm.onProgress().observe(lifecycleOwner, this::onProgress);
    }

    /**
     * Add a Calibre submenu with read/download/settings options as appropriate for the given Book.
     *
     * @param menu   to add to
     * @param book   to use
     * @param global Global preferences
     */
    public void prepareMenu(@NonNull final Menu menu,
                            @NonNull final Book book,
                            @NonNull final SharedPreferences global) {

        final boolean calibre = CalibreContentServer.isSyncEnabled(global)
                                && !book.getString(DBKey.KEY_CALIBRE_BOOK_UUID).isEmpty();

        menu.findItem(R.id.SUBMENU_CALIBRE).setVisible(calibre);
        if (calibre) {
            if (CalibreContentServer.getFolderUri(mView.getContext()).isPresent()) {
                // conditional
                menu.findItem(R.id.MENU_CALIBRE_READ)
                    .setVisible(existsLocally(book));

                // always shown
                menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD)
                    .setTitle(mView.getContext().getString(
                            R.string.menu_download_ebook_format,
                            book.getString(DBKey.KEY_CALIBRE_BOOK_MAIN_FORMAT)))
                    .setVisible(true);

                // don't show
                menu.findItem(R.id.MENU_CALIBRE_SETTINGS)
                    .setVisible(false);

            } else {
                // Calibre is enabled, but the folder is not set
                menu.findItem(R.id.MENU_CALIBRE_READ).setVisible(false);
                menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD).setVisible(false);
                menu.findItem(R.id.MENU_CALIBRE_SETTINGS)
                    .setTitle(R.string.menu_set_download_folder)
                    .setVisible(true);
            }

        } else {
            menu.findItem(R.id.MENU_CALIBRE_READ).setVisible(false);
            menu.findItem(R.id.MENU_CALIBRE_DOWNLOAD).setVisible(false);
            menu.findItem(R.id.MENU_CALIBRE_SETTINGS).setVisible(false);
        }
    }

    /**
     * Open the given book for reading.
     * This only works if the user has not renamed the file outside of this app.
     *
     * @param book to get
     */
    public void read(@NonNull final Book book) {
        try {
            openBookUri(getDocumentUri(mView.getContext(), book));

        } catch (@NonNull final FileNotFoundException e) {
            Snackbar.make(mView, R.string.httpErrorFileNotFound, Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Download the given book.
     * <p>
     * Will ask for a download folder if not done before.
     *
     * @param book to get
     */
    public void download(@NonNull final Book book) {
        final Optional<Uri> optionalUri = CalibreContentServer.getFolderUri(mView.getContext());
        if (optionalUri.isPresent()) {
            download(book, optionalUri.get());
        } else {
            mTempBookWhileRunningPickFolder = book;
            mPickFolderLauncher.launch(null);
        }
    }

    private void download(@NonNull final Book book,
                          @NonNull final Uri folder) {
        if (!mVm.download(book, folder)) {
            //TODO: better message
            Snackbar.make(mView, R.string.error_download_failed, Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Check if we have the book in the local folder.
     * This only works if the user has not renamed the file outside of this app.
     *
     * @param book to check
     *
     * @return {@code true} if we have the file
     */
    private boolean existsLocally(@NonNull final Book book) {
        try {
            getDocumentUri(mView.getContext(), book);
            return true;

        } catch (@NonNull final FileNotFoundException ignore) {
            return false;
        }
    }

    /**
     * Get the book file from the local folder.
     * This only works if the user has not renamed the file outside of this app.
     *
     * @param context Current context
     * @param book    to get
     *
     * @return book
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    private Uri getDocumentUri(@NonNull final Context context,
                               @NonNull final Book book)
            throws FileNotFoundException {

        final Optional<Uri> optFolderUri = CalibreContentServer.getFolderUri(context);
        if (optFolderUri.isPresent()) {
            try {
                return mServer.getDocumentFile(context, book, optFolderUri.get(), false).getUri();
            } catch (@NonNull final IOException e) {
                // Keep it simple.
                throw new FileNotFoundException(optFolderUri.get().toString());
            }
        }
        throw new FileNotFoundException("Folder not configured");
    }

    private void openBookUri(@NonNull final Uri uri) {
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        mView.getContext().startActivity(Intent.createChooser(
                intent, mView.getContext().getString(R.string.whichViewApplication)));
    }

    private void onFinished(@NonNull final FinishedMessage<Uri> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Snackbar.make(mView, R.string.progress_end_download_successful, Snackbar.LENGTH_LONG)
                    .setAction(R.string.lbl_read, v -> openBookUri(message.requireResult()))
                    .show();
        }
    }

    private void onCancelled(@NonNull final LiveDataEvent message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Snackbar.make(mView, R.string.cancelled, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final Exception e = message.getResult();

            final Context context = mView.getContext();
            final String msg = ExMsg.map(context, e)
                                    .orElse(context.getString(R.string.error_unknown));

            Snackbar.make(mView, msg, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.isNewEvent()) {
            if (mProgressDelegate == null) {
                mProgressDelegate = new ProgressDelegate(
                        mActivity.findViewById(R.id.progress_frame))
                        .setTitle(mActivity.getString(R.string.progress_msg_downloading))
                        .setPreventSleep(true)
                        .setIndeterminate(true)
                        .setOnCancelListener(v -> mVm.cancelTask(message.taskId))
                        .show(mActivity.getWindow());
            }
            mProgressDelegate.onProgress(message);
        }
    }

    private void closeProgressDialog() {
        if (mProgressDelegate != null) {
            mProgressDelegate.dismiss(mActivity.getWindow());
            mProgressDelegate = null;
        }
    }
}
