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
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
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
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.searches.UpdateFieldsTask;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminModel;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.UpdateFieldsModel;

import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.CopyIfBlank;
import static com.hardbacknutter.nevertoomanybooks.entities.FieldUsage.Usage.Overwrite;

/**
 * NEWTHINGS: This class must stay in sync with {@link UpdateFieldsTask}.
 * <p>
 * TODO: re-introduce remembering the last id done, and restarting from that id onwards.
 * See {@link UpdateFieldsModel} mFromBookIdOnwards
 */
public class UpdateFieldsFragment
        extends Fragment {

    public static final String TAG = "UpdateFieldsFragment";

    /** the ViewGroup where we'll add the list of fields. */
    private ViewGroup mFieldListView;

    private UpdateFieldsModel mUpdateFieldsModel;

    private ProgressDialogFragment mProgressDialog;
    private final UpdateFieldsModel.UpdateFieldsListener mUpdateFieldsListener =
            new UpdateFieldsModel.UpdateFieldsListener() {
                @Override
                public void onFinished(final boolean wasCancelled,
                                       @Nullable final Bundle data) {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }

                    if (wasCancelled) {
                        // This message will likely not be seen
                        //noinspection ConstantConditions
                        UserMessage.show(getView(), R.string.progress_end_cancelled);
                    }

                    if (data != null) {
                        //noinspection ConstantConditions
                        getActivity().setResult(Activity.RESULT_OK, new Intent().putExtras(data));
                    }
                    //noinspection ConstantConditions
                    getActivity().finish();
                }

                @Override
                public void onProgress(@NonNull final TaskListener.ProgressMessage message) {
                    if (mProgressDialog != null) {
                        mProgressDialog.onProgress(message);
                    }
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mandatory
        setHasOptionsMenu(true);
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

        mUpdateFieldsModel = new ViewModelProvider(this).get(UpdateFieldsModel.class);
        //noinspection ConstantConditions
        mUpdateFieldsModel.init(getContext(), getArguments(), mUpdateFieldsListener);

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            // reconnect after a fragment restart
            mProgressDialog.setCancellable(mUpdateFieldsModel.getTask());
        }

        // optional activity title
        if (getArguments() != null && getArguments().containsKey(UniqueId.BKEY_DIALOG_TITLE)) {
            //noinspection ConstantConditions
            activity.setTitle(getArguments().getString(UniqueId.BKEY_DIALOG_TITLE));
        } else {
            //noinspection ConstantConditions
            activity.setTitle(R.string.lbl_select_fields);
        }

        // FAB lives in Activity layout.
        FloatingActionButton fabButton = activity.findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_cloud_download);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> startUpdate());

        mUpdateFieldsModel.initFields();
        populateFields();

        if (savedInstanceState == null) {
            SearchSites.promptToRegister(getContext(), false, "update_from_internet",
                                         mUpdateFieldsModel.getSearchSites());

            TipManager.display(getContext(), R.string.tip_update_fields_from_internet, null);
        }

        // Warn the user, but don't abort.
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_network_no_connection);
        }
    }


//    @Override
//    @CallSuper
//    public void onResume() {
//        super.onResume();
//        if (getActivity() instanceof BaseActivity) {
//            BaseActivity activity = (BaseActivity) getActivity();
//            if (activity.isGoingToRecreate()) {
//                return;
//            }
//        }
//    }

    /**
     * Display the list of fields.
     */
    private void populateFields() {

        for (FieldUsage usage : mUpdateFieldsModel.getFieldUsages().values()) {
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
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case UniqueId.REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<Site> sites = data.getParcelableArrayListExtra(
                            SearchSites.BKEY_DATA_SITES);
                    if (sites != null) {
                        mUpdateFieldsModel.setSearchSites(sites);
                    }
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        Resources r = getResources();
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 r.getInteger(R.integer.MENU_ORDER_SEARCH_SITES),
                 R.string.lbl_websites)
            .setIcon(R.drawable.ic_find_in_page)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES: {
                Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminModel.BKEY_TABS_TO_SHOW, SearchAdminModel.TAB_BOOKS)
                        .putExtra(SearchSites.BKEY_DATA_SITES,
                                  mUpdateFieldsModel.getSearchSites());
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;
            }
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
    private void startUpdate() {
        // sanity check
        if (!hasSelections()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.warning_select_at_least_1_field);
            return;
        }

        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_network_no_connection);
            return;
        }

        // If the user has selected thumbnails...
        final FieldUsage covers = mUpdateFieldsModel.getFieldUsage(UniqueId.BKEY_IMAGE);
        if (covers != null && covers.getUsage().equals(Overwrite)) {
            // check if the user really wants to overwrite all covers
            new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.menu_update_fields)
                    .setMessage(R.string.confirm_overwrite_thumbnail)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .setNeutralButton(R.string.no, (dialog, which) -> {
                        covers.setUsage(CopyIfBlank);
                        mUpdateFieldsModel.putFieldUsage(UniqueId.BKEY_IMAGE, covers);
                        startSearch();
                    })
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        covers.setUsage(Overwrite);
                        mUpdateFieldsModel.putFieldUsage(UniqueId.BKEY_IMAGE, covers);
                        startSearch();
                    })
                    .create()
                    .show();
            return;
        }

        startSearch();
    }

    private void startSearch() {
        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment
                    .newInstance(R.string.progress_msg_searching, true, 0);
            mProgressDialog.show(fm, TAG);
            // Start the lookup in a background search task.
            if (mUpdateFieldsModel.startSearch()) {
                // we started at least one search.
                mProgressDialog.setCancellable(mUpdateFieldsModel.getTask());
            } else {
                Log.d(TAG, "onSearch was false");
                // unlikely... but paranoia.
                //TEST: the user might see a flash ??
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }
    }
}
