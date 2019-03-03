/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.filechooser;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.UserMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Fragment to display a simple directory/file browser.
 *
 * @author pjw
 */
public class FileChooserFragment
        extends Fragment
        implements FileListerAsyncTask.FileListerListener {

    /** Fragment manager tag. */
    public static final String TAG = FileChooserFragment.class.getSimpleName();

    private static final String BKEY_ROOT_PATH = "rootPath";
    private static final String BKEY_LIST = "list";

    private File mRootPath;
    private final OnClickListener onPathUpClickListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull final View v) {
            String parent = mRootPath.getParent();
            if (parent == null) {
                //Snackbar.make(requireView(),
                // R.string.no_parent_directory_found, Snackbar.LENGTH_LONG).show();
                UserMessage.showUserMessage(requireActivity(),
                                            R.string.warning_no_parent_directory_found);
                return;
            }
            mRootPath = new File(parent);

            tellActivityPathChanged();
        }
    };
    private EditText mFilenameField;
    private TextView mPathField;

    /** Create an empty one in case we are rotated before generated. */
    @Nullable
    private ArrayList<FileDetails> mList = new ArrayList<>();

    /** Create a new chooser fragment. */
    @NonNull
    public static FileChooserFragment newInstance(@NonNull final File root,
                                                  @NonNull final String fileName) {
        String path;
        // Turn the passed File into a directory
        if (root.isDirectory()) {
            path = root.getAbsolutePath();
        } else {
            path = root.getParent();
        }

        // Build the fragment and save the details
        FileChooserFragment frag = new FileChooserFragment();
        Bundle args = new Bundle();
        args.putString(BKEY_ROOT_PATH, path);
        args.putString(UniqueId.BKEY_FILE_SPEC, fileName);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports interface.
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnPathChangedListener)) {
            throw new RTE.MustImplementException(context, OnPathChangedListener.class);
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_chooser, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View view = requireView();
        mFilenameField = view.findViewById(R.id.file_name);
        mPathField = view.findViewById(R.id.path);

        if (savedInstanceState == null) {
            Bundle args = requireArguments();
            mRootPath = new File(Objects.requireNonNull(args.getString(BKEY_ROOT_PATH)));
            mFilenameField.setText(args.getString(UniqueId.BKEY_FILE_SPEC));
            mPathField.setText(mRootPath.getAbsolutePath());
            tellActivityPathChanged();
        } else {
            mRootPath =
                    new File(Objects.requireNonNull(savedInstanceState.getString(BKEY_ROOT_PATH)));
            ArrayList<FileDetails> list = savedInstanceState.getParcelableArrayList(BKEY_LIST);
            Objects.requireNonNull(list);
            onGotFileList(mRootPath, list);
        }

        // 'up' directory
        view.findViewById(R.id.btn_path_up).setOnClickListener(onPathUpClickListener);

        requireActivity().getWindow()
                         .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    /**
     * Convenience method to tell our activity the path has changed.
     */
    private void tellActivityPathChanged() {
        ((OnPathChangedListener) requireActivity()).onPathChanged(mRootPath);
    }

    /**
     * Save our root path and list.
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BKEY_ROOT_PATH, mRootPath.getAbsolutePath());
        outState.putParcelableArrayList(BKEY_LIST, mList);
    }

    /**
     * @return the selected file
     */
    @NonNull
    File getSelectedFile() {
        return new File(mRootPath.getAbsolutePath() +
                                File.separator + mFilenameField.getText().toString().trim());
    }

    /**
     * Display the list.
     *
     * @param root Root directory
     * @param list List of FileDetails
     */
    @Override
    public void onGotFileList(@NonNull final File root,
                              @NonNull final ArrayList<FileDetails> list) {
        mRootPath = root;
        mList = list;

        mPathField.setText(mRootPath.getAbsolutePath());

        // Setup and display the list
        ListAdapter adapter = new FileDetailsAdapter(requireContext(), mList);
        ListView lv = requireView().findViewById(android.R.id.list);
        lv.setAdapter(adapter);
    }


    /**
     * Interface that the containing Activity must implement. Called when user changes path.
     */
    public interface OnPathChangedListener {

        void onPathChanged(@NonNull File root);
    }

    /**
     * Interface for details of files in current directory.
     */
    public interface FileDetails
            extends Parcelable {

        /** Get the underlying File object. */
        @NonNull
        File getFile();

        void onGetView(@NonNull View convertView,
                       @NonNull Context context);
    }

    /**
     * List Adapter for FileDetails objects.
     */
    protected class FileDetailsAdapter
            extends ArrayAdapter<FileDetails> {

        FileDetailsAdapter(@NonNull final Context context,
                           @NonNull final ArrayList<FileDetails> items) {
            super(context, 0, items);
        }

        @NonNull
        @Override
        public View getView(final int position,
                            @Nullable View convertView,
                            @NonNull final ViewGroup parent) {

            final FileDetails item = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.row_file_info,
                                                                        parent, false);
            }

            //noinspection ConstantConditions
            item.onGetView(convertView, getContext());

            // Put the name of the file into the filename field when clicked.
            convertView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    if (item.getFile().isDirectory()) {
                        mRootPath = item.getFile();
                        tellActivityPathChanged();
                    } else {
                        mFilenameField.setText(item.getFile().getName());
                    }
                }
            });
            return convertView;
        }
    }
}
