package com.eleybourn.bookcatalogue;

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
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.entities.FieldUsage;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.UpdateFieldsFromInternetTask;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.settings.SearchAdminActivity;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTaskListener;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import static com.eleybourn.bookcatalogue.entities.FieldUsage.Usage.CopyIfBlank;
import static com.eleybourn.bookcatalogue.entities.FieldUsage.Usage.Overwrite;

/**
 * NEWKIND: must stay in sync with {@link UpdateFieldsFromInternetTask}.
 * <p>
 * FIXME: ... re-test and see why the progress stops when run on all books.
 * Seems we hit some limit in number of HTTP connections (server imposed ?)
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
                    // null if we did 'all books' or the ID's of the (hopefully) updated books.
                    .putExtra(UniqueId.BKEY_ID_LIST, mBookIds);

            Activity activity = getActivity();
            if (isSingleBook()) {
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
    private String mAuthorFormatted;
    /** display reminder only. */
    private String mTitle;

    private TaskManager mTaskManager;

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mTaskManager = ((BaseActivityWithTasks) context).getTaskManager();
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // make sure {@link #onCreateOptionsMenu} is called
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

    /** syntax sugar. */
    private boolean isSingleBook() {
        return (mBookIds != null && mBookIds.size() == 1);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            mSearchSites = args.getInt(REQUEST_BKEY_SEARCH_SITES, SearchSites.SEARCH_ALL);
            //noinspection unchecked
            mBookIds = (ArrayList<Long>) args.getSerializable(UniqueId.BKEY_ID_LIST);
            if (isSingleBook()) {
                // used for display only.
                mAuthorFormatted = args.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
                mTitle = args.getString(DBDefinitions.KEY_TITLE);
            }
        }

        if (isSingleBook()) {
            //noinspection ConstantConditions
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(mTitle);
                actionBar.setSubtitle(mAuthorFormatted);
            }
        } else {
            //noinspection ConstantConditions
            getActivity().setTitle(R.string.lbl_select_fields);
        }

        // FAB lives in Activity layout.
        FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_cloud_download);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> handleConfirm());

        initFields();
        populateFields();

        if ((mSearchSites & SearchSites.LIBRARY_THING) != 0) {
            //noinspection ConstantConditions
            LibraryThingManager.showLtAlertIfNecessary(getContext(), false,
                                                       "update_from_internet");
        }

        HintManager.displayHint(getLayoutInflater(),
                                R.string.hint_update_fields_from_internet, null);

        // Check general network connectivity. If none, WARN the user.
        if (!NetworkUtils.isNetworkAvailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_no_internet_connection);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(REQUEST_BKEY_SEARCH_SITES, mSearchSites);
        outState.putSerializable(UniqueId.BKEY_ID_LIST, mBookIds);
        outState.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, mAuthorFormatted);
        outState.putString(DBDefinitions.KEY_TITLE, mTitle);
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    private void initFields() {
        addListField(UniqueId.BKEY_AUTHOR_ARRAY,
                     DBDefinitions.KEY_FK_AUTHOR, R.string.lbl_author);

        addField(DBDefinitions.KEY_TITLE, CopyIfBlank, R.string.lbl_title);
        addField(DBDefinitions.KEY_ISBN, CopyIfBlank, R.string.lbl_isbn);
        addField(UniqueId.BKEY_COVER_IMAGE, CopyIfBlank, R.string.lbl_cover);

        addListField(UniqueId.BKEY_SERIES_ARRAY,
                     DBDefinitions.KEY_SERIES_TITLE, R.string.lbl_series);
        addListField(UniqueId.BKEY_TOC_ENTRY_ARRAY,
                     DBDefinitions.KEY_TOC_BITMASK, R.string.lbl_table_of_content);

        addField(DBDefinitions.KEY_PUBLISHER, CopyIfBlank, R.string.lbl_publisher);
        addField(DBDefinitions.KEY_DATE_PUBLISHED, CopyIfBlank, R.string.lbl_date_published);
        addField(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, CopyIfBlank,
                 R.string.lbl_first_publication);
        addField(DBDefinitions.KEY_DESCRIPTION, CopyIfBlank, R.string.lbl_description);

        addField(DBDefinitions.KEY_PAGES, CopyIfBlank, R.string.lbl_pages);
        addField(DBDefinitions.KEY_PRICE_LISTED, CopyIfBlank, R.string.lbl_price_listed);
        addField(DBDefinitions.KEY_FORMAT, CopyIfBlank, R.string.lbl_format);
        addField(DBDefinitions.KEY_GENRE, CopyIfBlank, R.string.lbl_genre);
        addField(DBDefinitions.KEY_LANGUAGE, CopyIfBlank, R.string.lbl_language);

        addField(DBDefinitions.KEY_ISFDB_ID, Overwrite, R.string.isfdb);
        addField(DBDefinitions.KEY_GOODREADS_ID, Overwrite, R.string.goodreads);
        addField(DBDefinitions.KEY_LIBRARY_THING_ID, Overwrite, R.string.library_thing);
        addField(DBDefinitions.KEY_OPEN_LIBRARY_ID, Overwrite, R.string.open_library);
    }

    /**
     * Add a FieldUsage for a <strong>simple</strong> field if it has not been hidden by the user.
     *
     * @param fieldId      Field name to use in FieldUsages + check for visibility
     * @param defaultUsage default Usage for this field
     * @param nameStringId Field label string resource id
     */
    private void addField(@NonNull final String fieldId,
                          final FieldUsage.Usage defaultUsage,
                          @StringRes final int nameStringId) {

        if (App.isUsed(fieldId)) {
            // CopyIfBlank by default, user can override.
            mFieldUsages.put(fieldId, new FieldUsage(fieldId, nameStringId, defaultUsage, false));
        }
    }

    /**
     * Add a FieldUsage for a <strong>list</strong> field if it has not been hidden by the user.
     * The default usage is always to append new data (Merge).
     *
     * @param fieldId      List-field name to use in FieldUsages
     * @param visField     Field name to check for visibility.
     * @param nameStringId Field label string resource id
     */
    private void addListField(@NonNull final String fieldId,
                              @NonNull final String visField,
                              @StringRes final int nameStringId) {

        if (App.isUsed(visField)) {
            // Merge by default, user can override.
            mFieldUsages.put(fieldId, new FieldUsage(fieldId, nameStringId,
                                                     FieldUsage.Usage.Merge, true));
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

            CompoundButton cb = row.findViewById(R.id.usage);
            cb.setChecked(usage.isSelected());
            cb.setText(usage.getUsageInfo(getContext()));
            cb.setTag(R.id.TAG_FIELD_USAGE, usage);
            cb.setOnClickListener(v -> {
                FieldUsage fieldUsage = (FieldUsage) cb.getTag(R.id.TAG_FIELD_USAGE);
                fieldUsage.nextState();
                cb.setChecked(fieldUsage.isSelected());
                cb.setText(fieldUsage.getUsageInfo(getContext()));
            });

            mFieldListView.addView(row);
        }
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

        Tracker.exitOnActivityResult(this);
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
            CompoundButton cb = view.findViewById(R.id.usage);
            if (cb != null) {
                FieldUsage fieldUsage = (FieldUsage) cb.getTag(R.id.TAG_FIELD_USAGE);
                if (fieldUsage.isSelected()) {
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
        final FieldUsage coversWanted = mFieldUsages.get(UniqueId.BKEY_COVER_IMAGE);
        //noinspection ConstantConditions
        if (isSingleBook() || !coversWanted.isSelected()) {
            // its a single book only; just download it.
            startUpdate();
        } else {
            // check if they really want to download ALL
            //noinspection ConstantConditions
            new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.lbl_update_fields)
                    .setMessage(R.string.confirm_overwrite_thumbnail)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setNeutralButton(R.string.no, (d, which) -> {
                        coversWanted.usage = CopyIfBlank;
                        mFieldUsages.put(UniqueId.BKEY_COVER_IMAGE, coversWanted);
                        startUpdate();
                    })
                    .setPositiveButton(R.string.yes, (d, which) -> {
                        coversWanted.usage = Overwrite;
                        mFieldUsages.put(UniqueId.BKEY_COVER_IMAGE, coversWanted);
                        startUpdate();
                    })
                    .create()
                    .show();
        }
    }

    private void startUpdate() {

        // Don't start search if we have no approved network... FAIL.
        if (!NetworkUtils.isNetworkAvailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_no_internet_connection);
            return;
        }

        UpdateFieldsFromInternetTask updateTask =
                new UpdateFieldsFromInternetTask(mTaskManager, mSearchSites, mFieldUsages,
                                                 mManagedTaskListener);

        updateTask.setBookId(mBookIds);

        mUpdateSenderId = updateTask.getSenderId();
        UpdateFieldsFromInternetTask.MESSAGE_SWITCH.addListener(mUpdateSenderId, false,
                                                                mManagedTaskListener);
        updateTask.start();
    }

    @Override
    @CallSuper
    public void onPause() {
        if (mUpdateSenderId != 0) {
            UpdateFieldsFromInternetTask.MESSAGE_SWITCH.removeListener(mUpdateSenderId,
                                                                       mManagedTaskListener);
        }
        super.onPause();
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        if (mUpdateSenderId != 0) {
            UpdateFieldsFromInternetTask.MESSAGE_SWITCH.addListener(mUpdateSenderId, true,
                                                                    mManagedTaskListener);
        }
    }
}
