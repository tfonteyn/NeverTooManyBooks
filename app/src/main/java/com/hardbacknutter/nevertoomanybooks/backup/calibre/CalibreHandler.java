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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

/**
 * A delegate class for handling a Calibre enabled Book.
 */
public class CalibreHandler {

    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> mPickFolderLauncher;

    /** Task to download the Calibre eBook. */
    private CalibreSingleFileDownload mCalibreSingleFileDownload;

    /** ONLY USED AND VALID WHILE RUNNING THE {@link #mPickFolderLauncher}. */
    @Nullable
    private Book mTempBookWhileRunningPickFolder;

    private View mView;

    public void onViewCreated(@NonNull final View view,
                              @NonNull final ActivityResultCaller caller,
                              @NonNull final ViewModelStoreOwner viewModelStoreOwner,
                              @NonNull final LifecycleOwner lifecycleOwner) {
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

        mCalibreSingleFileDownload = new ViewModelProvider(viewModelStoreOwner)
                .get(CalibreSingleFileDownload.class);
        mCalibreSingleFileDownload.onProgressUpdate().observe(lifecycleOwner, this::onProgress);
        mCalibreSingleFileDownload.onCancelled().observe(lifecycleOwner, this::onCancelled);
        mCalibreSingleFileDownload.onFailure().observe(lifecycleOwner, this::onCalibreFailure);
        mCalibreSingleFileDownload.onFinished().observe(lifecycleOwner, this::onCalibreFinished);
    }

    public boolean isCalibreEnabled(@NonNull final DataHolder book) {
        return !book.getString(DBDefinitions.KEY_CALIBRE_BOOK_UUID).isEmpty();
    }

    public boolean isCalibreEnabled(@NonNull final SharedPreferences global,
                                    @NonNull final DataHolder book) {
        return CalibreContentServer.isShowSyncMenus(global)
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
        Snackbar.make(mView, R.string.progress_msg_connecting,
                      Snackbar.LENGTH_LONG).show();
        mCalibreSingleFileDownload.start(book, folder);
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.text != null) {
            Snackbar.make(mView, message.text, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onCancelled(@NonNull final LiveDataEvent message) {
        if (message.isNewEvent()) {
            Snackbar.make(mView, R.string.cancelled, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onCalibreFailure(@NonNull final FinishedMessage<Exception> message) {
        if (message.isNewEvent()) {
            final String msg;
            if (message.result != null) {
                msg = message.result.toString();
            } else {
                msg = mView.getContext().getString(R.string.error_unknown);
            }
            Snackbar.make(mView, msg, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onCalibreFinished(@NonNull final FinishedMessage<Uri> message) {
        if (message.isNewEvent()) {
            if (message.result != null) {
                Snackbar.make(mView, R.string.done, Snackbar.LENGTH_LONG)
                        .setAction(R.string.lbl_read, v -> mView.getContext().startActivity(
                                new Intent(Intent.ACTION_VIEW).setData(message.result)))
                        .show();
            } else {
                Snackbar.make(mView, R.string.error_import_failed,
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

}
