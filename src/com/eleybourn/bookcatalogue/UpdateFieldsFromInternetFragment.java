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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Tracker;
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

/**
 * NEWKIND: must stay in sync with {@link UpdateFieldsFromInternetTask}.
 * <p>
 * FIXME: ... re-test and see why the progress stops.
 * Seems we hit some limit in number of HTTP connections (server imposed ?)
 */
public class UpdateFieldsFromInternetFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "UpdateFieldsFromInternetFragment";

    /** RequestCode for editing the search sites order. */
    private static final int REQ_PREFERRED_SEARCH_SITES = 0;

    /**
     * optionally limit the sites to search on.
     * By default uses {@link SearchSites#SEARCH_ALL}
     */
    private static final String REQUEST_BKEY_SEARCH_SITES = TAG + ":SearchSites";
    /** which fields to update and how. */
    private final Map<String, FieldUsage> mFieldUsages = new LinkedHashMap<>();
    /** where to look. */
    private int mSearchSites = SearchSites.SEARCH_ALL;
    /** 0 for all books, or a specific book. */
    private long mBookId;

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
                    // 0 if we did 'all books' or the id of the (hopefully) updated book.
                    .putExtra(DBDefinitions.KEY_ID, mBookId);

            Activity activity = requireActivity();
            if (mBookId == 0) {
                // task cancelled does not mean that nothing was done.
                // Books *will* be updated until the cancelling happened
                activity.setResult(Activity.RESULT_OK, data);
            } else {
                // but if a single book was cancelled, flag that up
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
        //noinspection ConstantConditions
        mTaskManager = ((BaseActivityWithTasks) getActivity()).getTaskManager();
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

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            mSearchSites = args.getInt(REQUEST_BKEY_SEARCH_SITES, SearchSites.SEARCH_ALL);
            mBookId = args.getLong(DBDefinitions.KEY_ID, 0L);
            if (mBookId > 0) {
                mAuthorFormatted = args.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
                mTitle = args.getString(DBDefinitions.KEY_TITLE);
            }
        }

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_select_fields);

        View root = requireView();
        TextView authorView = root.findViewById(R.id.author);
        TextView titleView = root.findViewById(R.id.title);
        // we're only requesting ONE book to be updated.
        if (mBookId > 0) {
            authorView.setText(mAuthorFormatted);
            authorView.setVisibility(View.VISIBLE);
            titleView.setText(mTitle);
            titleView.setVisibility(View.VISIBLE);
        } else {
            authorView.setVisibility(View.GONE);
            titleView.setVisibility(View.GONE);
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

        // Check general network connectivity. If none, WARN the user.
        if (!NetworkUtils.isNetworkAvailable()) {
            UserMessage.showUserMessage(requireView(), R.string.error_no_internet_connection);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(REQUEST_BKEY_SEARCH_SITES, mSearchSites);
        outState.putLong(DBDefinitions.KEY_ID, mBookId);
        outState.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, mAuthorFormatted);
        outState.putString(DBDefinitions.KEY_TITLE, mTitle);
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    private void initFields() {
        addIfVisible(UniqueId.BKEY_AUTHOR_ARRAY, DBDefinitions.KEY_AUTHOR,
                     R.string.lbl_author, FieldUsage.Usage.AddExtra, true);
        addIfVisible(DBDefinitions.KEY_TITLE,
                     R.string.lbl_title, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_ISBN,
                     R.string.lbl_isbn, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.BKEY_COVER_IMAGE,
                     R.string.lbl_cover, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.BKEY_SERIES_ARRAY, DBDefinitions.KEY_SERIES,
                     R.string.lbl_series, FieldUsage.Usage.AddExtra, true);
        addIfVisible(UniqueId.BKEY_TOC_ENTRY_ARRAY, DBDefinitions.KEY_TOC_BITMASK,
                     R.string.lbl_table_of_content, FieldUsage.Usage.AddExtra, true);
        addIfVisible(DBDefinitions.KEY_PUBLISHER,
                     R.string.lbl_publisher, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_DATE_PUBLISHED,
                     R.string.lbl_date_published, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_DATE_FIRST_PUBLISHED,
                     R.string.lbl_first_publication, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_DESCRIPTION,
                     R.string.lbl_description, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_PAGES,
                     R.string.lbl_pages, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_PRICE_LISTED,
                     R.string.lbl_price_listed, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_FORMAT,
                     R.string.lbl_format, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_GENRE,
                     R.string.lbl_genre, FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_LANGUAGE,
                     R.string.lbl_language, FieldUsage.Usage.CopyIfBlank, false);
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param fieldId      name to use in FieldUsages + check for visibility
     * @param nameStringId of field label string
     * @param defaultUsage Usage to apply.
     * @param isList       if the field is a list to which we can append to
     */
    private void addIfVisible(@NonNull final String fieldId,
                              @StringRes final int nameStringId,
                              @NonNull final FieldUsage.Usage defaultUsage,
                              final boolean isList) {

        if (App.isUsed(fieldId)) {
            mFieldUsages.put(fieldId, new FieldUsage(fieldId, nameStringId, defaultUsage, isList));
        }
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param fieldId      name to use in FieldUsages
     * @param visField     Field name to check for visibility.
     * @param nameStringId of field label string
     * @param defaultUsage Usage to apply.
     * @param isList       if the field is a list to which we can append to
     */
    private void addIfVisible(@NonNull final String fieldId,
                              @NonNull final String visField,
                              @StringRes final int nameStringId,
                              @NonNull final FieldUsage.Usage defaultUsage,
                              final boolean isList) {

        if (App.isUsed(visField)) {
            mFieldUsages.put(fieldId, new FieldUsage(fieldId, nameStringId, defaultUsage, isList));
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
                 MenuHandler.MENU_ORDER_SEARCH_SITES, R.string.lbl_search_sites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {

//            case R.id.MENU_BOOK_UPDATE_FROM_INTERNET:
//                handleConfirm();
//                return true;

            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminActivity.REQUEST_BKEY_TAB,
                                  SearchAdminActivity.TAB_ORDER);
                startActivityForResult(intent, REQ_PREFERRED_SEARCH_SITES);
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
            case REQ_PREFERRED_SEARCH_SITES:
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
            UserMessage.showUserMessage(requireView(), R.string.warning_select_min_1_field);
            return;
        }

        // If the user has selected thumbnails, check if they want to download ALL
        final FieldUsage coversWanted = mFieldUsages.get(UniqueId.BKEY_COVER_IMAGE);
        // but don't ask if its a single book only; just download it.
        //noinspection ConstantConditions
        if (mBookId == 0 && coversWanted.isSelected()) {
            // Verify - this can be a dangerous operation
            //noinspection ConstantConditions
            new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.lbl_update_fields)
                    .setMessage(R.string.confirm_overwrite_thumbnail)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setNeutralButton(R.string.no, (d, which) -> {
                        coversWanted.usage = FieldUsage.Usage.CopyIfBlank;
                        startUpdate(mBookId);
                    })
                    .setPositiveButton(R.string.yes, (d, which) -> {
                        coversWanted.usage = FieldUsage.Usage.Overwrite;
                        startUpdate(mBookId);
                    })
                    .create()
                    .show();
        } else {
            startUpdate(mBookId);
        }
    }

    /**
     * TODO: allow the use of {@link UpdateFieldsFromInternetTask#setBookId(List)}.
     *
     * @param bookId 0 for all books, or a valid book id for one book
     */
    private void startUpdate(final long bookId) {

        // Don't start search if we have no approved network... FAIL.
        if (!NetworkUtils.isNetworkAvailable()) {
            UserMessage.showUserMessage(requireView(), R.string.error_no_internet_connection);
            return;
        }

        UpdateFieldsFromInternetTask updateTask =
                new UpdateFieldsFromInternetTask(mTaskManager, mSearchSites, mFieldUsages,
                                                 mManagedTaskListener);
        if (bookId > 0) {
            updateTask.setBookId(bookId);
        }

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
