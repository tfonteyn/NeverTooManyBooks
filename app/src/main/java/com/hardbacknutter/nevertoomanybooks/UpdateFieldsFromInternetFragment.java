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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivityWithTasks;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.UpdateFieldsFromInternetTask;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.ManagedTask;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.ManagedTaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.CopyIfBlank;
import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.Overwrite;

/**
 * NEWKIND: must stay in sync with {@link UpdateFieldsFromInternetTask}.
 */
public class UpdateFieldsFromInternetFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "UpdateFieldsFromInternetFragment";

    /**
     * optionally limit the sites to search on.
     * By default uses {@link SearchSites#SEARCH_ALL}
     */
    private static final String REQUEST_BKEY_SEARCH_SITES = TAG + ":SearchSites";
    /** which fields to update and how. */
    private final Map<String, FieldUsage> mFieldUsages = new LinkedHashMap<>();
    /** where to look. */
    private int mSearchSites = SearchSites.SEARCH_ALL;
    /** Book ID's to fetch. {@code null} for all books. */
    private ArrayList<Long> mBookIds;

    /** the ViewGroup where we'll add the list of fields. */
    private ViewGroup mFieldListView;
    /** senderId of the update task. */
    private long mUpdateSenderId;
    private final ManagedTaskListener mManagedTaskListener = new ManagedTaskListener() {
        @Override
        public void onTaskFinished(@NonNull final ManagedTask task) {
            mUpdateSenderId = 0;
            Intent data = new Intent()
                                  .putExtra(UniqueId.BKEY_CANCELED, task.isCancelled())
                                  // null if we did 'all books'
                                  // or the ID's of the (hopefully) updated books.
                                  .putExtra(UniqueId.BKEY_ID_LIST, mBookIds);

            Activity activity = getActivity();
            if (!isSingleBook()) {
                // task cancelled does not mean that nothing was done.
                // Books *will* be updated until the cancelling happened
                //noinspection ConstantConditions
                activity.setResult(Activity.RESULT_OK, data);
            } else {
                // but if a single book was cancelled, flag that up
                //noinspection ConstantConditions
                activity.setResult(Activity.RESULT_CANCELED, data);
            }
            activity.finish();
        }
    };
    /** display reminder only. */
    private String mTitle;

    private TaskManager mTaskManager;

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mTaskManager = ((BaseActivityWithTasks) context).getTaskManager();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mandatory
        setHasOptionsMenu(true);

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : getArguments();
        if (currentArgs != null) {
            //noinspection unchecked
            mBookIds = (ArrayList<Long>) currentArgs.getSerializable(UniqueId.BKEY_ID_LIST);

            // optional
            mSearchSites = currentArgs.getInt(REQUEST_BKEY_SEARCH_SITES, SearchSites.SEARCH_ALL);
            // optional activity title
            mTitle = currentArgs.getString(UniqueId.BKEY_DIALOG_TITLE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_update_from_internet, container, false);
        mFieldListView = view.findViewById(R.id.manage_fields_scrollview);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = getActivity();
        if (mTitle != null) {
            //noinspection ConstantConditions
            activity.setTitle(mTitle);
        } else {
            //noinspection ConstantConditions
            activity.setTitle(R.string.lbl_select_fields);
        }

        // FAB lives in Activity layout.
        FloatingActionButton fabButton = activity.findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_cloud_download);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> handleConfirm());

        initFields();
        populateFields();

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            SearchSites.alertRegistrationBeneficial(getContext(), "update_from_internet",
                                                    mSearchSites);

            TipManager.display(getContext(), R.string.tip_update_fields_from_internet, null);
        }

        // Check general network connectivity. If none, WARN the user.
        if (NetworkUtils.networkUnavailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_no_internet_connection);
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        if (mUpdateSenderId != 0) {
            UpdateFieldsFromInternetTask.MESSAGE_SWITCH
                    .addListener(mUpdateSenderId, true, mManagedTaskListener);
        }
    }

    /** syntax sugar. */
    private boolean isSingleBook() {
        return mBookIds != null && mBookIds.size() == 1;
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    private void initFields() {

        addListField(R.string.lbl_author, DBDefinitions.KEY_FK_AUTHOR,
                     UniqueId.BKEY_AUTHOR_ARRAY);

        addField(R.string.lbl_title, CopyIfBlank, DBDefinitions.KEY_TITLE);
        addField(R.string.lbl_isbn, CopyIfBlank, DBDefinitions.KEY_ISBN);
        addField(R.string.lbl_cover, CopyIfBlank, UniqueId.BKEY_IMAGE);

        addListField(R.string.lbl_series, DBDefinitions.KEY_SERIES_TITLE,
                     UniqueId.BKEY_SERIES_ARRAY);

        addListField(R.string.lbl_table_of_content, DBDefinitions.KEY_TOC_BITMASK,
                     UniqueId.BKEY_TOC_ENTRY_ARRAY);

        addField(R.string.lbl_publisher, CopyIfBlank,
                 DBDefinitions.KEY_PUBLISHER);
        addField(R.string.lbl_date_published, CopyIfBlank,
                 DBDefinitions.KEY_DATE_PUBLISHED);
        addField(R.string.lbl_first_publication, CopyIfBlank,
                 DBDefinitions.KEY_DATE_FIRST_PUBLICATION);

        addField(R.string.lbl_description, CopyIfBlank, DBDefinitions.KEY_DESCRIPTION);

        addField(R.string.lbl_pages, CopyIfBlank, DBDefinitions.KEY_PAGES);
        addField(R.string.lbl_format, CopyIfBlank, DBDefinitions.KEY_FORMAT);
        addField(R.string.lbl_language, CopyIfBlank, DBDefinitions.KEY_LANGUAGE);

        // list price has related DBDefinitions.KEY_PRICE_LISTED
        addField(R.string.lbl_price_listed, CopyIfBlank, DBDefinitions.KEY_PRICE_LISTED);

        addField(R.string.lbl_genre, CopyIfBlank, DBDefinitions.KEY_GENRE);

        addField(R.string.isfdb, Overwrite, DBDefinitions.KEY_ISFDB_ID);
        addField(R.string.goodreads, Overwrite, DBDefinitions.KEY_GOODREADS_BOOK_ID);
        addField(R.string.library_thing, Overwrite, DBDefinitions.KEY_LIBRARY_THING_ID);
        addField(R.string.open_library, Overwrite, DBDefinitions.KEY_OPEN_LIBRARY_ID);
    }

    /**
     * Add a FieldUsage for a <strong>simple</strong> field if it has not been hidden by the user.
     *
     * @param nameStringId Field label string resource ID
     * @param defaultUsage default Usage for this field
     * @param fieldId      Field name to use in FieldUsages + check for visibility
     */
    private void addField(@StringRes final int nameStringId,
                          @NonNull final FieldUsage.Usage defaultUsage,
                          @NonNull final String fieldId) {

        if (App.isUsed(fieldId)) {
            mFieldUsages.put(fieldId, new FieldUsage(nameStringId, defaultUsage,
                                                     false, fieldId));
        }
    }

    /**
     * Add a FieldUsage for a <strong>list</strong> field if it has not been hidden by the user.
     *
     * @param nameStringId Field label string resource ID
     * @param visField     Field name to check for visibility.
     * @param fieldId      List-field name to use in FieldUsages
     */
    private void addListField(@StringRes final int nameStringId,
                              @NonNull final String visField,
                              @NonNull final String fieldId) {

        if (App.isUsed(visField)) {
            mFieldUsages.put(fieldId, new FieldUsage(nameStringId, FieldUsage.Usage.Append,
                                                     true, fieldId));
        }
    }

    /**
     * Called from {@link #startUpdate} to add any related fields with the same setting.
     *
     * @param fieldId        to check presence of
     * @param relatedFieldId to add if fieldId was present
     */
    private void addRelatedField(@SuppressWarnings("SameParameterValue")
                                 @NonNull final String fieldId,
                                 @SuppressWarnings("SameParameterValue")
                                 @NonNull final String relatedFieldId) {
        FieldUsage field = mFieldUsages.get(fieldId);
        if (field != null && field.isWanted()) {
            mFieldUsages.put(relatedFieldId, new FieldUsage(0, field.getUsage(),
                                                            field.canAppend(), relatedFieldId));
        }
    }

    /**
     * Display the list of fields.
     */
    private void populateFields() {

        for (FieldUsage usage : mFieldUsages.values()) {
            View row = getLayoutInflater().inflate(R.layout.row_update_from_internet,
                                                   mFieldListView, false);

            TextView fieldLabel = row.findViewById(R.id.field);
            //noinspection ConstantConditions
            fieldLabel.setText(usage.getLabel(getContext()));

            CompoundButton cb = row.findViewById(R.id.cbx_usage);
            cb.setChecked(usage.isWanted());
            cb.setText(usage.getUsageInfo(getContext()));
            cb.setTag(R.id.TAG_FIELD_USAGE, usage);
            cb.setOnClickListener(v -> {
                FieldUsage fieldUsage = (FieldUsage) cb.getTag(R.id.TAG_FIELD_USAGE);
                fieldUsage.nextState();
                cb.setChecked(fieldUsage.isWanted());
                cb.setText(fieldUsage.getUsageInfo(getContext()));
            });

            mFieldListView.addView(row);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case UniqueId.REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    mSearchSites = data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES,
                                                    mSearchSites);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        //noinspection ConstantConditions
        LocaleUtils.insanityCheck(getContext());
        Tracker.exitOnActivityResult(this);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(UniqueId.BKEY_ID_LIST, mBookIds);
        outState.putInt(REQUEST_BKEY_SEARCH_SITES, mSearchSites);
        outState.putString(UniqueId.BKEY_DIALOG_TITLE, mTitle);
    }

    @Override
    @CallSuper
    public void onPause() {
        if (mUpdateSenderId != 0) {
            UpdateFieldsFromInternetTask.MESSAGE_SWITCH
                    .removeListener(mUpdateSenderId, mManagedTaskListener);
        }
        super.onPause();
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 MenuHandler.ORDER_SEARCH_SITES, R.string.lbl_search_sites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                                        .putExtra(SearchAdminActivity.REQUEST_BKEY_TAB,
                                                  SearchAdminActivity.TAB_ORDER);
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Count the checked fields, we need at least one selected to make sense.
     *
     * @return {@code true} if at least one field is selected
     */
    private boolean hasSelections() {
        int nChildren = mFieldListView.getChildCount();
        for (int i = 0; i < nChildren; i++) {
            View view = mFieldListView.getChildAt(i);
            CompoundButton cb = view.findViewById(R.id.cbx_usage);
            if (cb != null) {
                FieldUsage fieldUsage = (FieldUsage) cb.getTag(R.id.TAG_FIELD_USAGE);
                if (fieldUsage.isWanted()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * After confirmation, start the process.
     */
    private void handleConfirm() {
        // sanity check
        if (!hasSelections()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.warning_select_min_1_field);
            return;
        }

        // If the user has selected thumbnails...
        final FieldUsage covers = mFieldUsages.get(UniqueId.BKEY_IMAGE);

        //noinspection ConstantConditions
        if (covers.getUsage().equals(Overwrite)) {
            // check if the user really wants to overwrite all covers
            //noinspection ConstantConditions
            new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.menu_update_fields)
                    .setMessage(R.string.confirm_overwrite_thumbnail)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setNeutralButton(R.string.no, (d, which) -> {
                        covers.setUsage(CopyIfBlank);
                        mFieldUsages.put(UniqueId.BKEY_IMAGE, covers);
                        startUpdate();
                    })
                    .setPositiveButton(R.string.yes, (d, which) -> {
                        covers.setUsage(Overwrite);
                        mFieldUsages.put(UniqueId.BKEY_IMAGE, covers);
                        startUpdate();
                    })
                    .create()
                    .show();
            return;
        }

        startUpdate();
    }

    private void startUpdate() {

        // Don't start search if we have no approved network... FAIL.
        if (NetworkUtils.networkUnavailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_no_internet_connection);
            return;
        }

        // add related fields.
        // i.e. if we do the 'list-price' field, we'll also want its currency.
        addRelatedField(DBDefinitions.KEY_PRICE_LISTED, DBDefinitions.KEY_PRICE_LISTED_CURRENCY);

        UpdateFieldsFromInternetTask updateTask =
                new UpdateFieldsFromInternetTask(mTaskManager, mSearchSites, mFieldUsages,
                                                 mManagedTaskListener);
        updateTask.setBookId(mBookIds);
        mUpdateSenderId = updateTask.getSenderId();
        UpdateFieldsFromInternetTask.MESSAGE_SWITCH
                .addListener(mUpdateSenderId, false, mManagedTaskListener);
        updateTask.start();
    }
}
