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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.syncing.SyncProcessor;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

public class StripinfoSyncViewModel
        extends ViewModel {

    private static final String TAG = "CollectionImporterVM";
    private static final String SYNC_PROCESSOR_PREFIX = "stripinfo.fields.update.";

    private final CollectionImporterTask mCollectionImporterTask = new CollectionImporterTask();

    private BookDao mBookDao;

    private SyncProcessor mSyncProcessor;

    @Override
    protected void onCleared() {
        mCollectionImporterTask.cancel(true);

        if (mBookDao != null) {
            mBookDao.close();
        }

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Localized context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        if (mBookDao == null) {
            mBookDao = new BookDao(TAG);

            //ENHANCE: make these user configurable.
            mSyncProcessor = new SyncProcessor.Builder(SYNC_PROCESSOR_PREFIX)
                    .add(R.string.site_stripinfo_be, DBKeys.KEY_ESID_STRIP_INFO_BE)

                    .add(R.string.lbl_cover_front, DBKeys.COVER_IS_USED[0])
                    .add(R.string.lbl_cover_back, DBKeys.COVER_IS_USED[1])

                    // the wishlist
                    .addList(R.string.lbl_bookshelves, DBKeys.KEY_FK_BOOKSHELF,
                             Book.BKEY_BOOKSHELF_LIST)

                    .add(R.string.lbl_date_acquired, DBKeys.KEY_DATE_ACQUIRED)
                    .add(R.string.lbl_location, DBKeys.KEY_LOCATION)
                    .add(R.string.lbl_personal_notes, DBKeys.KEY_PRIVATE_NOTES)
                    .add(R.string.lbl_rating, DBKeys.KEY_RATING)
                    .add(R.string.lbl_read, DBKeys.KEY_READ)

                    .add(R.string.lbl_price_paid, DBKeys.KEY_PRICE_PAID)
                    .build(mBookDao);

            mSyncProcessor.addRelatedField(R.string.lbl_currency, DBKeys.KEY_PRICE_PAID_CURRENCY,
                                           DBKeys.KEY_PRICE_PAID);

            mSyncProcessor.addRelatedField(R.string.lbl_cover_front, DBKeys.COVER_IS_USED[0],
                                           Book.BKEY_TMP_FILE_SPEC[0]);
            mSyncProcessor.addRelatedField(R.string.lbl_cover_back, DBKeys.COVER_IS_USED[1],
                                           Book.BKEY_TMP_FILE_SPEC[1]);
        }
    }

    void fetchCollection() {
        mCollectionImporterTask.fetch(mSyncProcessor);
    }

    @NonNull
    LiveData<ProgressMessage> onProgress() {
        return mCollectionImporterTask.onProgressUpdate();
    }

    @NonNull
    LiveData<FinishedMessage<List<Long>>> onImportCancelled() {
        return mCollectionImporterTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onImportFailure() {
        return mCollectionImporterTask.onFailure();
    }

    @NonNull
    LiveData<FinishedMessage<List<Long>>> onImportFinished() {
        return mCollectionImporterTask.onFinished();
    }

    public void connectProgressDialog(@NonNull final ProgressDialogFragment dialog) {
        dialog.setCanceller(mCollectionImporterTask);
    }
}
