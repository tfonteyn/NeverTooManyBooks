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
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.filechooser.FileLister.FileListerListener;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter.ViewProvider;

import java.io.File;
import java.util.ArrayList;

/**
 * Fragment to display a simple directory/file browser.
 * 
 * @author pjw
 *
 */
public class FileChooserFragment extends Fragment implements FileListerListener {
	private File mRootPath;
	protected static final String BKEY_ROOT_PATH = "rootPath";
	protected static final String BKEY_FILE_NAME = "fileName";
	protected static final String BKEY_LIST = "list";

	// Create an empty one in case we are rotated before generated.
	protected ArrayList<FileDetails> mList = new ArrayList<>();

	/**
	 * Interface that the containing Activity must implement. Called when user changes path.
	 *
	 * @author pjw
	 */
	public interface PathChangedListener {
		void onPathChanged(File root);
	}

	/** Create a new chooser fragment */
	public static FileChooserFragment newInstance(String rootPath, String fileName) {
		return newInstance(new File(rootPath), fileName);
	}

	/** Create a new chooser fragment */
	public static FileChooserFragment newInstance(File root, String fileName) {
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
        args.putString(BKEY_FILE_NAME, fileName);
        frag.setArguments(args);

        return frag;
	}

	/** Interface for details of files in current directory */
	public interface FileDetails extends ViewProvider, Parcelable {
		/** Get the underlying File object */
		File getFile();
		/** Called to fill in the details of this object in the View provided by the ViewProvider implementation */
		void onSetupView(Context context, int position, View target);
	}

	/**
	 * Ensure activity supports event
	 */
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		if (! (PathChangedListener.class.isInstance(context)) )
			throw new RuntimeException("Class " + context.getClass().getSimpleName() + " must implement " + PathChangedListener.class.getSimpleName());
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.file_chooser, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Handle the 'up' item; go to the next directory up
		final View root = getView();
		root.findViewById(R.id.up).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				handleUp();
			}
		});

		// If it's new, just build from scratch, otherwise, get the saved directory and list
		if (savedInstanceState == null) {
			mRootPath = new File(getArguments().getString(BKEY_ROOT_PATH));
			String fileName = getArguments().getString(BKEY_FILE_NAME);
			EditText filenameField = root.findViewById(R.id.file_name);
			filenameField.setText(fileName);
			((TextView) root.findViewById(R.id.path)).setText(mRootPath.getAbsolutePath());
			tellActivityPathChanged();
		} else {
			mRootPath = new File(savedInstanceState.getString(BKEY_ROOT_PATH));
			ArrayList<FileDetails> list = savedInstanceState.getParcelableArrayList(BKEY_LIST);
			this.onGotFileList(mRootPath, list);
		}
	}

	/**
	 * Convenience method to tell our activity the path has changed.
	 */
	private void tellActivityPathChanged() {	
		((PathChangedListener)getActivity()).onPathChanged(mRootPath);
	}

	/**
	 * Handle the 'Up' action
	 */
	private void handleUp() {
		String parent = mRootPath.getParent();
		if (parent == null) {
			Toast.makeText(getActivity(), R.string.no_parent_directory_found, Toast.LENGTH_LONG).show();
			return;
		}
		mRootPath = new File(parent);
		
		tellActivityPathChanged();
	}

	/**
	 * Save our root path and list
	 */
	@Override
	public void onSaveInstanceState(@NonNull Bundle state) {
		super.onSaveInstanceState(state);
		state.putString(BKEY_ROOT_PATH, mRootPath.getAbsolutePath());
		state.putParcelableArrayList(BKEY_LIST, mList);
	}

	/**
	 * List Adapter for FileDetails objects
	 * 
	 * @author pjw
	 */
	public class DirectoryAdapter extends SimpleListAdapter<FileDetails> {

		DirectoryAdapter(Context context, int rowViewId, ArrayList<FileDetails> items) {
			super(context, rowViewId, items);
		}

		@Override
		protected void onSetupView(FileDetails fileDetails, int position, View target) {
			fileDetails.onSetupView(getActivity(), position, target);
		}

		@Override
		protected void onRowClick(FileDetails fileDetails, int position, View v) {
			if (fileDetails != null) {
				if (fileDetails.getFile().isDirectory()) {
					mRootPath = fileDetails.getFile();
					tellActivityPathChanged();
				} else {
					EditText et = FileChooserFragment.this.getView().findViewById(R.id.file_name);
					et.setText(fileDetails.getFile().getName());
				}
			}
		}
	}

	/** 
	 * Accessor
	 * 
	 * @return File
	 */
	public File getSelectedFile() {
		EditText et = getView().findViewById(R.id.file_name);
		return new File(mRootPath.getAbsolutePath() + "/" + et.getText().toString());
	}

	/**
	 * Display the list
	 * 
	 * @param root		Root directory
	 * @param list		List of FileDetails
	 */
	@Override
	public void onGotFileList(File root, ArrayList<FileDetails> list) {
		mRootPath = root;
		((TextView) getView().findViewById(R.id.path)).setText(mRootPath.getAbsolutePath());

		// Setup and display the list
		mList = list;
		// We pass 0 as view ID since each item can provide the view id
		DirectoryAdapter adapter = new DirectoryAdapter(getActivity(), 0, mList);
		ListView lv = getView().findViewById(android.R.id.list);
		lv.setAdapter(adapter);
	}

}
