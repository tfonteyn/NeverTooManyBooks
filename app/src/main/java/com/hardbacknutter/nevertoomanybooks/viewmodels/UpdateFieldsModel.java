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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.searches.UpdateFieldsTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.CopyIfBlank;
import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.Overwrite;

public class UpdateFieldsModel
        extends ViewModel {

    private static final String TAG = "UpdateFieldsModel";

    /** which fields to update and how. */
    @NonNull
    private final Map<String, FieldUsage> mFieldUsages = new LinkedHashMap<>();
    private final Handler mHandler = new Handler();
    /** Database Access. */
    private DAO mDb;
    /** Listener for the UpdateFieldsTask. */
    private final TaskListener<Long> mTaskListener = new TaskListener<Long>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Long> message) {
            mUpdateTask = null;

            boolean isCancelled = message.status == TaskStatus.Cancelled;

            // the last book id which was handled; can be used to restart the update.
            mFromBookIdOnwards = message.result;

            if (mBookIds != null && mBookIds.size() == 1 && isCancelled) {
                // single book cancelled, just quit
                mHandler.post(() -> {
                    if (mUpdateFieldsListener.get() != null) {
                        mUpdateFieldsListener.get().onFinished(true, null);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Log.d(TAG, "onFinished|" + Logger.WEAK_REFERENCE_DEAD);
                        }
                    }
                });
                return;
            }

            Bundle data = new Bundle();
            // null if we did 'all books'
            // or the ID's (1 or more) of the (hopefully) updated books
            data.putSerializable(UniqueId.BKEY_ID_LIST, mBookIds);
            // One or more books were changed.
            // Technically speaking when doing a list of books,
            // the task might have been cancelled before the first
            // book was done. We disregard this fringe case.
            data.putBoolean(UniqueId.BKEY_BOOK_MODIFIED, true);

            if (mBookIds != null && !mBookIds.isEmpty()) {
                // Pass the first book for reposition the list (if applicable)
                data.putLong(DBDefinitions.KEY_PK_ID, mBookIds.get(0));
            }

            mHandler.post(() -> {
                if (mUpdateFieldsListener.get() != null) {
                    mUpdateFieldsListener.get().onFinished(isCancelled, data);
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                        Log.d(TAG, "onFinished|" + Logger.WEAK_REFERENCE_DEAD);
                    }
                }
            });
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            mHandler.post(() -> {
                if (mUpdateFieldsListener.get() != null) {
                    mUpdateFieldsListener.get().onProgress(message);
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                        Log.d(TAG, "onProgress|" + Logger.WEAK_REFERENCE_DEAD);
                    }
                }
            });
        }
    };
    /** Book ID's to fetch. {@code null} for all books. */
    @Nullable
    private ArrayList<Long> mBookIds;
    /** Allows restarting an update task from the given book id onwards. 0 for all. */
    private long mFromBookIdOnwards;
    private UpdateFieldsTask mUpdateTask;
    private WeakReference<UpdateFieldsListener> mUpdateFieldsListener;
    /** Sites to search on. */
    private SiteList mSiteList;

    @Override
    protected void onCleared() {
        if (mUpdateTask != null) {
            mUpdateTask.interrupt();
        }

        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args,
                     @NonNull final UpdateFieldsListener updateFieldsListener) {

        mUpdateFieldsListener = new WeakReference<>(updateFieldsListener);

        if (mSiteList == null) {

            mDb = new DAO();
            // use global preference.
            mSiteList = SiteList.getList(context, SiteList.ListType.Data);

            if (args != null) {
                //noinspection unchecked
                mBookIds = (ArrayList<Long>) args.getSerializable(UniqueId.BKEY_ID_LIST);
            }
        }
    }

    @Nullable
    public FieldUsage getFieldUsage(@NonNull final String key) {
        return mFieldUsages.get(key);
    }

    @NonNull
    public Map<String, FieldUsage> getFieldUsages() {
        return mFieldUsages;
    }

    public void putFieldUsage(@NonNull final String key,
                              @NonNull final FieldUsage fieldUsage) {
        mFieldUsages.put(key, fieldUsage);
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list
     */
    @NonNull
    public SiteList getSiteList() {
        return mSiteList;
    }

    /**
     * Override the initial list.
     *
     * @param siteList to use temporarily
     */
    public void setSiteList(@NonNull final SiteList siteList) {
        mSiteList = siteList;
    }

    public void setFromBookIdOnwards(final long fromBookIdOnwards) {
        mFromBookIdOnwards = fromBookIdOnwards;
    }

    public long getLastBookIdDone() {
        return mFromBookIdOnwards;
    }

    /**
     * Add any related fields with the same setting.
     * <p>
     * We enforce a name (string id), although it's never displayed, for sanity/debug sake.
     *
     * @param primaryFieldId the field to check
     * @param relatedFieldId to add if the primary field is present
     * @param nameStringId   Field label string resource ID
     */
    @SuppressWarnings("SameParameterValue")
    private void addRelatedField(@NonNull final String primaryFieldId,
                                 @NonNull final String relatedFieldId,
                                 @StringRes final int nameStringId) {
        FieldUsage field = mFieldUsages.get(primaryFieldId);
        if (field != null && field.isWanted()) {
            FieldUsage fu = new FieldUsage(relatedFieldId, nameStringId,
                                           field.getUsage(), field.canAppend());
            mFieldUsages.put(relatedFieldId, fu);
        }
    }

    /**
     * Add a FieldUsage for a <strong>list</strong> field if it has not been hidden by the user.
     * <p>
     * The default usage for a list field is {@link FieldUsage.Usage#Append}.
     *
     * @param fieldId      List-field name to use in FieldUsages
     * @param nameStringId Field label string resource ID
     * @param visField     Field name to check for visibility.
     */
    private void addListField(@NonNull final String fieldId,
                              @StringRes final int nameStringId,
                              @NonNull final String visField) {

        if (App.isUsed(visField)) {
            FieldUsage fu = new FieldUsage(fieldId, nameStringId, FieldUsage.Usage.Append, true);
            mFieldUsages.put(fieldId, fu);
        }
    }

    /**
     * Add a FieldUsage for a <strong>simple</strong> field if it has not been hidden by the user.
     *
     * @param fieldId      Field name to use in FieldUsages + check for visibility
     * @param nameStringId Field label string resource ID
     * @param defaultUsage default Usage for this field
     */
    private void addField(@NonNull final String fieldId,
                          @StringRes final int nameStringId,
                          @NonNull final FieldUsage.Usage defaultUsage) {

        if (App.isUsed(fieldId)) {
            putFieldUsage(fieldId, new FieldUsage(fieldId, nameStringId, defaultUsage, false));
        }
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    public void initFields() {

        addListField(UniqueId.BKEY_AUTHOR_ARRAY, R.string.lbl_author, DBDefinitions.KEY_FK_AUTHOR);

        addField(DBDefinitions.KEY_TITLE, R.string.lbl_title, CopyIfBlank);
        addField(DBDefinitions.KEY_ISBN, R.string.lbl_isbn, CopyIfBlank);
        addField(UniqueId.BKEY_IMAGE, R.string.lbl_cover, CopyIfBlank);

        addListField(UniqueId.BKEY_SERIES_ARRAY, R.string.lbl_series,
                     DBDefinitions.KEY_SERIES_TITLE);

        addListField(UniqueId.BKEY_TOC_ENTRY_ARRAY, R.string.lbl_table_of_content,
                     DBDefinitions.KEY_TOC_BITMASK);

        addListField(UniqueId.BKEY_PUBLISHER_ARRAY, R.string.lbl_publisher,
                     DBDefinitions.KEY_PUBLISHER);

        addField(DBDefinitions.KEY_PRINT_RUN, R.string.lbl_print_run, CopyIfBlank);
        addField(DBDefinitions.KEY_DATE_PUBLISHED, R.string.lbl_date_published, CopyIfBlank);
        addField(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, R.string.lbl_first_publication,
                 CopyIfBlank);

        // list price has related DBDefinitions.KEY_PRICE_LISTED
        addField(DBDefinitions.KEY_PRICE_LISTED, R.string.lbl_price_listed, CopyIfBlank);

        addField(DBDefinitions.KEY_DESCRIPTION, R.string.lbl_description, CopyIfBlank);

        addField(DBDefinitions.KEY_PAGES, R.string.lbl_pages, CopyIfBlank);
        addField(DBDefinitions.KEY_FORMAT, R.string.lbl_format, CopyIfBlank);
        addField(DBDefinitions.KEY_COLOR, R.string.lbl_color, CopyIfBlank);
        addField(DBDefinitions.KEY_LANGUAGE, R.string.lbl_language, CopyIfBlank);
        addField(DBDefinitions.KEY_GENRE, R.string.lbl_genre, CopyIfBlank);

        //NEWTHINGS: add new site specific ID: add a field
        addField(DBDefinitions.KEY_EID_ISFDB, R.string.isfdb, Overwrite);
        addField(DBDefinitions.KEY_EID_GOODREADS_BOOK, R.string.goodreads, Overwrite);
        addField(DBDefinitions.KEY_EID_LIBRARY_THING, R.string.library_thing, Overwrite);
        addField(DBDefinitions.KEY_EID_OPEN_LIBRARY, R.string.open_library, Overwrite);
        addField(DBDefinitions.KEY_EID_STRIP_INFO_BE, R.string.stripinfo, Overwrite);
    }

    public UpdateFieldsTask getTask() {
        return mUpdateTask;
    }

    public boolean startSearch() {
        // add related fields.
        // i.e. if we do the 'list-price' field, we'll also want its currency.
        addRelatedField(DBDefinitions.KEY_PRICE_LISTED,
                        DBDefinitions.KEY_PRICE_LISTED_CURRENCY, R.string.lbl_currency);

        mUpdateTask = new UpdateFieldsTask(mDb, mSiteList, mFieldUsages, mTaskListener);

        if (mBookIds != null) {
            //update just these
            mUpdateTask.setBookId(mBookIds);
        } else {
            //update the complete library starting from the given id
            mUpdateTask.setFromBookIdOnwards(mFromBookIdOnwards);
        }

        mUpdateTask.start();
        return true;
    }

    /**
     * Allows other objects get updates on the search.
     */
    public interface UpdateFieldsListener {

        /**
         * Called when all individual search tasks are finished.
         * <p>
         * Task cancelled does not mean that nothing was done.
         * Books *will* be updated until the cancelling happened
         *
         * @param wasCancelled if we were cancelled.
         * @param data         resulting data, can be empty
         */
        void onFinished(boolean wasCancelled,
                        @Nullable Bundle data);

        /**
         * Progress messages.
         */
        void onProgress(@NonNull TaskListener.ProgressMessage message);
    }
}
