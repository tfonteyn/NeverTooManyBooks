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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchTask;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.searches.UpdateFieldsTask;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.CopyIfBlank;
import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.Overwrite;

/**
 * Work flow for the {@link TaskListener.FinishMessage}.
 * <ol>
 * <li>Fragment tells the {@link UpdateFieldsModel} to start the search</li>
 * <li>{@link UpdateFieldsModel} starts an {@link UpdateFieldsTask} (a plain Thread)
 * passing in its {@link TaskListener}</li>
 * <li>{@link UpdateFieldsTask} hosts a {@link SearchCoordinator}
 * passing in its {@link SearchCoordinator.SearchCoordinatorListener}</li>
 * <li>{@link SearchCoordinator} starts/stops {@link SearchTask}
 * and handles the results from them.</li>
 * <li>{@link SearchCoordinator} returns its results to the {@link UpdateFieldsTask}</li>
 * <li>{@link UpdateFieldsTask} loops to do all books</li>
 * <li>When the {@link UpdateFieldsTask} is all done, it returns its status to the
 * {@link UpdateFieldsModel}</li>
 * <li>{@link UpdateFieldsModel} <strong>POSTS</strong> the results to the MutableLiveData</li>
 * <li>the fragment gets updated and acts on the final status</li>
 * </ol>
 * <p>
 * Work flow for the {@link TaskListener.ProgressMessage}.
 * <ol>
 * <li>{@link SearchTask} sends to {@link SearchCoordinator}</li>
 * <li>{@link SearchCoordinator} sends to {@link UpdateFieldsTask}</li>
 * <li>{@link UpdateFieldsTask} sends to {@link UpdateFieldsModel}</li>
 * <li>{@link UpdateFieldsModel} <strong>POSTS</strong> MutableLiveData value</li>
 * <li>Fragment use a {@link ProgressDialogFragment}</li>
 * </ol>
 * <p>
 * Complicated ? Not really. Cumbersome? Absolutely.
 */
public class UpdateFieldsModel
        extends ViewModel {

    private static final String TAG = "UpdateFieldsModel";

    public static final String BKEY_LAST_BOOK_ID = TAG + ":lastId";

    /** which fields to update and how. */
    @NonNull
    private final Map<String, FieldUsage> mFieldUsages = new LinkedHashMap<>();
    private final MutableLiveData<TaskListener.FinishMessage<Bundle>>
            mTaskFinishedMessage = new MutableLiveData<>();
    private final MutableLiveData<TaskListener.ProgressMessage>
            mTaskProgressMessage = new MutableLiveData<>();
    /** Database Access. */
    private DAO mDb;
    /** Book ID's to fetch. {@code null} for all books. */
    @Nullable
    private ArrayList<Long> mBookIds;
    /** Allows restarting an update task from the given book id onwards. 0 for all. */
    private long mFromBookIdOnwards;
    private UpdateFieldsTask mUpdateTask;
    /**
     * Listener for the UpdateFieldsTask.
     *
     * <strong>Note:</strong> we must use postValue as we're
     * getting called from within {@link UpdateFieldsTask} which is a plain Thread.
     */
    private final TaskListener<Bundle> mTaskListener = new TaskListener<Bundle>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Bundle> message) {
            mUpdateTask = null;

            // the last book id which was handled; can be used to restart the update.
            mFromBookIdOnwards = message.result.getLong(UpdateFieldsModel.BKEY_LAST_BOOK_ID);

            // reminder: the taskId wil be the one from the UpdateFieldsTask.
            mTaskFinishedMessage.postValue(message);
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            // reminder: the taskId wil be the one from the SearchTask.
            mTaskProgressMessage.postValue(message);
        }
    };
    /** Sites to search on. */
    private SiteList mSiteList;

    public MutableLiveData<TaskListener.ProgressMessage> getTaskProgressMessage() {
        return mTaskProgressMessage;
    }

    public MutableLiveData<TaskListener.FinishMessage<Bundle>> getTaskFinishedMessage() {
        return mTaskFinishedMessage;
    }

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
                     @Nullable final Bundle args) {

        if (mSiteList == null) {

            mDb = new DAO();
            // use global preference.
            mSiteList = SiteList.getList(context, SiteList.Type.Data);

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

    @SuppressWarnings("SameReturnValue")
    public boolean startSearch() {
        // add related fields.
        // i.e. if we do the 'list-price' field, we'll also want its currency.
        addRelatedField(DBDefinitions.KEY_PRICE_LISTED,
                        DBDefinitions.KEY_PRICE_LISTED_CURRENCY, R.string.lbl_currency);

        mUpdateTask = new UpdateFieldsTask(mDb, mSiteList, mFieldUsages, mTaskListener);

        if (mBookIds != null) {
            //update just these (1 or more)
            mUpdateTask.setBookId(mBookIds);

        } else {
            //update the complete library starting from the given id
            mUpdateTask.setFromBookIdOnwards(mFromBookIdOnwards);
        }

        mUpdateTask.start();
        return true;
    }
}
