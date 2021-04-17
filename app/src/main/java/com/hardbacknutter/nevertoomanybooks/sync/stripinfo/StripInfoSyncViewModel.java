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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.SyncProcessor;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

public class StripInfoSyncViewModel
        extends ViewModel {

    private static final String TAG = "CollectionImporterVM";
    private static final String SYNC_PROCESSOR_PREFIX = StripInfoAuth.PREF_KEY + ".fields.update.";

    private ImportCollectionTask mImportCollectionTask;

    private SyncProcessor mSyncProcessor;
    private boolean[] mCoversForNewBooks;

    @Override
    protected void onCleared() {
        mImportCollectionTask.cancel(true);

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        if (mImportCollectionTask == null) {
            mImportCollectionTask = new ImportCollectionTask();

            //ENHANCE: make these user configurable. The simple fields are CopyIfBlank
            mSyncProcessor = new SyncProcessor.Config(SYNC_PROCESSOR_PREFIX)
                    .add(R.string.site_stripinfo_be, DBKey.SID_STRIP_INFO)

                    .add(R.string.lbl_cover_front, DBKey.COVER_IS_USED[0])
                    .addRelatedField(DBKey.COVER_IS_USED[0], Book.BKEY_TMP_FILE_SPEC[0])
                    .add(R.string.lbl_cover_back, DBKey.COVER_IS_USED[1])
                    .addRelatedField(DBKey.COVER_IS_USED[1], Book.BKEY_TMP_FILE_SPEC[1])

                    // the wishlist
                    .addList(R.string.lbl_bookshelves, DBKey.FK_BOOKSHELF,
                             Book.BKEY_BOOKSHELF_LIST)

                    .add(R.string.lbl_date_acquired, DBKey.DATE_ACQUIRED)
                    .add(R.string.lbl_location, DBKey.KEY_LOCATION)
                    .add(R.string.lbl_personal_notes, DBKey.KEY_PRIVATE_NOTES)
                    .add(R.string.lbl_rating, DBKey.KEY_RATING)
                    .add(R.string.lbl_read, DBKey.BOOL_READ)

                    .add(R.string.lbl_price_paid, DBKey.PRICE_PAID)
                    .addRelatedField(DBKey.PRICE_PAID_CURRENCY, DBKey.PRICE_PAID)

                    // The site specific keys
                    .add(R.string.lbl_owned, DBKey.BOOL_STRIP_INFO_OWNED)
                    .add(R.string.lbl_wishlist, DBKey.BOOL_STRIP_INFO_WANTED)
                    .add(R.string.lbl_number, DBKey.KEY_STRIP_INFO_AMOUNT)
                    .add(R.string.site_stripinfo_be, DBKey.KEY_STRIP_INFO_COLL_ID)

                    .build();

            final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
            mCoversForNewBooks = new boolean[]{DBKey.isUsed(global, DBKey.COVER_IS_USED[0]),
                                               DBKey.isUsed(global, DBKey.COVER_IS_USED[1])};
        }
    }

    void startImport() {
        mImportCollectionTask.startImport(mSyncProcessor, mCoversForNewBooks);
    }

    @NonNull
    LiveData<ProgressMessage> onImportCollectionProgress() {
        return mImportCollectionTask.onProgressUpdate();
    }

    @NonNull
    LiveData<FinishedMessage<ImportCollectionTask.Outcome>> onImportCollectionFinished() {
        return mImportCollectionTask.onFinished();
    }

    @NonNull
    LiveData<FinishedMessage<ImportCollectionTask.Outcome>> onImportCollectionCancelled() {
        return mImportCollectionTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onImportFailure() {
        return mImportCollectionTask.onFailure();
    }

    void linkTaskWithDialog(@IdRes final int taskId,
                            @NonNull final ProgressDialogFragment dialog) {
        if (taskId == mImportCollectionTask.getTaskId()) {
            dialog.setCanceller(mImportCollectionTask);

        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
