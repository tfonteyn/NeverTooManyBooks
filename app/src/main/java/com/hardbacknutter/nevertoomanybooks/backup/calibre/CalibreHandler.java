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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

/**
 * A delegate class for handling a Calibre enabled Book.
 */
public class CalibreHandler {

    private static final String TAG = "CalibreHandler";
    /** The Calibre API object. */
    @NonNull
    private final CalibreContentServer mServer;
    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> mPickFolderLauncher;
    /** Task to download the Calibre eBook. */
    private CalibreSingleFileDownload mFileDownload;
    /** ONLY USED AND VALID WHILE RUNNING THE {@link #mPickFolderLauncher}. */
    @Nullable
    private Book mTempBookWhileRunningPickFolder;
    /** The host view; used for context, resources, Snackbar. */
    private View mView;
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    private FragmentManager mFragmentManager;

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @throws IOException          on failures
     * @throws CertificateException on failures related to the user installed CA.
     */
    public CalibreHandler(@NonNull final Context context)
            throws IOException, CertificateException {
        mServer = new CalibreContentServer(context);
    }

    public void onViewCreated(@NonNull final FragmentActivity activity,
                              @NonNull final View view) {
        onViewCreated(view, activity, activity, activity.getSupportFragmentManager(), activity);
    }

    public void onViewCreated(@NonNull final Fragment fragment) {
        //noinspection ConstantConditions
        onViewCreated(fragment.getView(), fragment.getViewLifecycleOwner(),
                      fragment, fragment.getChildFragmentManager(), fragment);
    }

    /**
     * Host (Fragment/Activity) independent initializer.
     *
     * @param view the hosting component root view
     */
    public void onViewCreated(@NonNull final View view,
                              @NonNull final LifecycleOwner lifecycleOwner,
                              @NonNull final ViewModelStoreOwner viewModelStoreOwner,
                              @NonNull final FragmentManager fm,
                              @NonNull final ActivityResultCaller caller) {
        mView = view;
        mFragmentManager = fm;

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

        mFileDownload = new ViewModelProvider(viewModelStoreOwner)
                .get(CalibreSingleFileDownload.class);
        mFileDownload.init(mServer);

        mFileDownload.onProgressUpdate().observe(lifecycleOwner, this::onProgress);
        mFileDownload.onCancelled().observe(lifecycleOwner, this::onCancelled);
        mFileDownload.onFailure().observe(lifecycleOwner, this::onFailure);
        mFileDownload.onFinished().observe(lifecycleOwner, this::onFinished);
    }

    public boolean isCalibreEnabled(@NonNull final DataHolder book) {
        return !book.getString(DBDefinitions.KEY_CALIBRE_BOOK_UUID).isEmpty();
    }

    public boolean isCalibreEnabled(@NonNull final SharedPreferences global,
                                    @NonNull final DataHolder book) {
        return CalibreContentServer.isEnabled(global)
               && !book.getString(DBDefinitions.KEY_CALIBRE_BOOK_UUID).isEmpty();
    }

    /**
     * Download the given book.
     * <p>
     * Will ask for a download folder if not done before.
     *
     * @param book to get
     */
    public void download(@NonNull final Book book) {
        final Optional<Uri> optionalUri = mServer.getFolderUri(mView.getContext());
        if (optionalUri.isPresent()) {
            download(book, optionalUri.get());
        } else {
            mTempBookWhileRunningPickFolder = book;
            mPickFolderLauncher.launch(null);
        }
    }

    private void download(@NonNull final Book book,
                          @NonNull final Uri folder) {
        if (!mFileDownload.start(book, folder)) {
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
    public boolean existsLocally(@NonNull final Book book) {
        try {
            mServer.getDocumentUri(mView.getContext(), book);
            return true;

        } catch (@NonNull final FileNotFoundException ignore) {
            return false;
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
            openBookUri(mServer.getDocumentUri(mView.getContext(), book));

        } catch (@NonNull final FileNotFoundException e) {
            //TODO: better message and/or start the download?
            Snackbar.make(mView, R.string.httpErrorFileNotFound, Snackbar.LENGTH_LONG).show();
            // download(book);
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {

        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog();
        }
        mProgressDialog.onProgress(message);
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog() {
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                mFragmentManager.findFragmentByTag(ProgressDialogFragment.TAG);
        // not found? create it
        if (dialog == null) {
            dialog = ProgressDialogFragment.newInstance(
                    mView.getContext().getString(R.string.menu_download_file), true, true);
            dialog.show(mFragmentManager, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCanceller(mFileDownload);
        return dialog;
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
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
            String msg = ExMsg.map(mView.getContext(), TAG, message.result);
            if (msg == null) {
                msg = mView.getContext().getString(R.string.error_unknown);
            }
            Snackbar.make(mView, msg, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onFinished(@NonNull final FinishedMessage<Uri> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final Uri uri = message.result;

            if (uri != null) {
                Snackbar.make(mView, R.string.progress_end_download_successful,
                              Snackbar.LENGTH_LONG)
                        .setAction(R.string.lbl_read, v -> openBookUri(uri))
                        .show();
            } else {
                Snackbar.make(mView, R.string.error_download_failed,
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void openBookUri(@NonNull final Uri uri) {
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        mView.getContext().startActivity(intent);
    }
}
