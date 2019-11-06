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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivityWithTasks;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.searches.UpdateFieldsFromInternetTask;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminModel;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.ManagedTask;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.ManagedTaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.UpdateFieldsFromInternetModel;

import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.CopyIfBlank;
import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.Overwrite;

/**
 * NEWTHINGS: This class must stay in sync with {@link UpdateFieldsFromInternetTask}.
 * <p>
 * TODO: re-introduce remembering the last id done, and restarting from that id onwards.
 * See {@link UpdateFieldsFromInternetModel} mFromBookIdOnwards
 */
public class UpdateFieldsFromInternetFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "UpdateFieldsFromInternetFragment";

    /** the ViewGroup where we'll add the list of fields. */
    private ViewGroup mFieldListView;

    private TaskManager mTaskManager;

    private UpdateFieldsFromInternetModel mModel;

    private final ManagedTaskListener mManagedTaskListener = new ManagedTaskListener() {
        @Override
        public void onTaskFinished(@NonNull final ManagedTask task) {
            mModel.setUpdateSenderId(0);

            Activity activity = getActivity();

            if (mModel.isSingleBook() && task.isCancelled()) {
                // Nothing was changed, just quit
                //noinspection ConstantConditions
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
                return;
            }

            ArrayList<Long> bookIds = mModel.getBookIds();

//            // the last book id which was handled; can be used to restart the update.
//            long lastBookId = ((UpdateFieldsFromInternetTask)task).getLastBookIdDone();
//            boolean fullListDone =
//                    (bookIds != null && !bookIds.isEmpty())
//                    && bookIds.get(bookIds.size()-1) == lastBookId;

            Intent data = new Intent()
                    // null if we did 'all books'
                    // or the ID's (1 or more) of the (hopefully) updated books
                    .putExtra(UniqueId.BKEY_ID_LIST, bookIds)
                    // task cancelled does not mean that nothing was done.
                    // Books *will* be updated until the cancelling happened
                    .putExtra(UniqueId.BKEY_CANCELED, task.isCancelled())
                    // One or more books were changed.
                    // Technically speaking when doing a list of books,
                    // the task might have been cancelled before the first
                    // book was done. We disregard this fringe case.
                    .putExtra(UniqueId.BKEY_BOOK_MODIFIED, true);

            if (bookIds != null && !bookIds.isEmpty()) {
                // Pass the first book for reposition the list (if applicable)
                data.putExtra(DBDefinitions.KEY_PK_ID, bookIds.get(0));
            }

            //noinspection ConstantConditions
            activity.setResult(Activity.RESULT_OK, data);

            activity.finish();
        }
    };

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

        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(UpdateFieldsFromInternetModel.class);
        //noinspection ConstantConditions
        mModel.init(getContext(), getArguments());
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
        if (mModel.getTitle() != null) {
            //noinspection ConstantConditions
            activity.setTitle(mModel.getTitle());
        } else {
            //noinspection ConstantConditions
            activity.setTitle(R.string.lbl_select_fields);
        }

        // FAB lives in Activity layout.
        FloatingActionButton fabButton = activity.findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_cloud_download);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> handleConfirm());

        mModel.initFields();
        populateFields();

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            SearchSites.alertRegistrationBeneficial(getContext(), "update_from_internet",
                                                    mModel.getEnabledSearchSites());

            TipManager.display(getContext(), R.string.tip_update_fields_from_internet, null);
        }

        // Check general network connectivity. If none, WARN the user.
        if (NetworkUtils.networkUnavailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_network_no_connection);
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Logger.debugEnter(this, "onResume");
        }
        super.onResume();
        if (getActivity() instanceof BaseActivity) {
            BaseActivity activity = (BaseActivity) getActivity();
            if (activity.isGoingToRecreate()) {
                return;
            }
        }

        if (mModel.getUpdateSenderId() != 0) {
            UpdateFieldsFromInternetTask.MESSAGE_SWITCH
                    .addListener(mModel.getUpdateSenderId(), true, mManagedTaskListener);
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Logger.debugExit(this, "onResume");
        }
    }

    /**
     * Display the list of fields.
     */
    private void populateFields() {

        for (FieldUsage usage : mModel.getFieldUsages().values()) {
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
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(this, requestCode, resultCode, data);
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case UniqueId.REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<Site> sites = data.getParcelableArrayListExtra(
                            SearchSites.BKEY_SEARCH_SITES_BOOKS);
                    if (sites != null) {
                        mModel.setSearchSites(sites);
                    }
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        //noinspection ConstantConditions
        LocaleUtils.insanityCheck(getContext());
    }

    @Override
    @CallSuper
    public void onPause() {
        if (mModel.getUpdateSenderId() != 0) {
            UpdateFieldsFromInternetTask.MESSAGE_SWITCH
                    .removeListener(mModel.getUpdateSenderId(), mManagedTaskListener);
        }
        super.onPause();
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 MenuHandler.ORDER_SEARCH_SITES, R.string.lbl_websites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminModel.BKEY_TABS_TO_SHOW, SearchAdminModel.TAB_BOOKS)
                        .putExtra(SearchSites.BKEY_SEARCH_SITES_BOOKS,
                                  mModel.getSearchSites());
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
            UserMessage.show(getView(), R.string.warning_select_at_least_1_field);
            return;
        }

        // If the user has selected thumbnails...
        final FieldUsage covers = mModel.getFieldUsage(UniqueId.BKEY_IMAGE);
        if (covers != null && covers.getUsage().equals(Overwrite)) {
            // check if the user really wants to overwrite all covers
            //noinspection ConstantConditions
            new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.menu_update_fields)
                    .setMessage(R.string.confirm_overwrite_thumbnail)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .setNeutralButton(R.string.no, (dialog, which) -> {
                        covers.setUsage(CopyIfBlank);
                        mModel.putFieldUsage(UniqueId.BKEY_IMAGE, covers);
                        startUpdate();
                    })
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        covers.setUsage(Overwrite);
                        mModel.putFieldUsage(UniqueId.BKEY_IMAGE, covers);
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
            UserMessage.show(getView(), R.string.error_network_no_connection);
            return;
        }

        // add related fields.
        // i.e. if we do the 'list-price' field, we'll also want its currency.
        mModel.addRelatedField(DBDefinitions.KEY_PRICE_LISTED,
                               DBDefinitions.KEY_PRICE_LISTED_CURRENCY);

        UpdateFieldsFromInternetTask updateTask =
                new UpdateFieldsFromInternetTask(mTaskManager,
                                                 mModel.getSearchSites(),
                                                 mModel.getFieldUsages(),
                                                 mManagedTaskListener);
        ArrayList<Long> list = mModel.getBookIds();
        if (list != null) {
            updateTask.setBookId(list);
        } else {
            // the complete library starting from the given id
            updateTask.setCurrentBookId(mModel.getFromBookIdOnwards());
        }

        mModel.setUpdateSenderId(updateTask.getSenderId());
        UpdateFieldsFromInternetTask.MESSAGE_SWITCH
                .addListener(mModel.getUpdateSenderId(), false, mManagedTaskListener);
        updateTask.start();
    }
}
