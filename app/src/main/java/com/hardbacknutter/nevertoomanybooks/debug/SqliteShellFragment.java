/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSqliteShellBinding;

/**
 * A crude sql shell.
 */
public class SqliteShellFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "SqliteShellFragment";

    public static final String BKEY_ALLOW_UPDATES = TAG + ":upd";

    private static final int MAX_LINES = 200;
    private static final String UTF_8 = "utf-8";
    private static final String TEXT_HTML = "text/html";
    private static final String STR_LAST_COUNT = "Rows: ";
    private static final String STR_ROWS_AFFECTED = "Rows affected: ";
    private static final String STR_UPDATES_NOT_ALLOWED = "Updates are not allowed";

    private static final String SQL_LIST_TABLES =
            "SELECT '' AS T, tbl_name FROM sqlite_master WHERE type='table'"
            + " UNION "
            + "SELECT 'T' AS T, tbl_name FROM sqlite_temp_master WHERE type='table'"
            + " ORDER BY tbl_name";

    private boolean mAllowUpdates;

    /** View binding. */
    private FragmentSqliteShellBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        final Bundle args = getArguments();
        if (args != null) {
            mAllowUpdates = args.getBoolean(BKEY_ALLOW_UPDATES);
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentSqliteShellBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final WebSettings settings = mVb.output.getSettings();
        settings.setTextZoom(75);
    }

//    private void textSmaller() {
//        final WebSettings settings = outputView.getSettings();
//        settings.setTextZoom(settings.getTextZoom() - 10);
//    }
//
//    private void textBigger() {
//        final WebSettings settings = outputView.getSettings();
//        settings.setTextZoom(settings.getTextZoom() + 10);
//    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_DEBUG_SQ_SHELL_RUN, 0, R.string.debug_sq_shell_run)
            .setIcon(mAllowUpdates ? R.drawable.ic_warning : R.drawable.ic_directions_run)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.MENU_DEBUG_SQ_SHELL_LIST_TABLES, 0,
                 R.string.debug_sq_shell_list_tables)
            .setIcon(R.drawable.ic_format_list_bulleted)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_DEBUG_SQ_SHELL_RUN: {
                executeSql(mVb.input.getText().toString().trim());
                return true;
            }
            case R.id.MENU_DEBUG_SQ_SHELL_LIST_TABLES: {
                executeSql(SQL_LIST_TABLES);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void executeSql(@NonNull final String sql) {
        final String lcSql = sql.toLowerCase(Locale.ROOT);
        try {
            if (lcSql.startsWith("update") || lcSql.startsWith("delete")) {
                //noinspection ConstantConditions
                getActivity().setTitle("");

                if (mAllowUpdates) {
                    try (SynchronizedStatement stmt = DAO.getSyncDb().compileStatement(sql)) {
                        final int rowsAffected = stmt.executeUpdateDelete();
                        final String result = STR_ROWS_AFFECTED + rowsAffected;
                        mVb.output.loadDataWithBaseURL(null, result,
                                                       TEXT_HTML, UTF_8, null);
                    }
                } else {
                    mVb.output.loadDataWithBaseURL(null, STR_UPDATES_NOT_ALLOWED,
                                                   TEXT_HTML, UTF_8, null);
                }
            } else {
                try (Cursor cursor = DAO.getSyncDb().rawQuery(sql, null)) {
                    final String title = STR_LAST_COUNT + cursor.getCount();
                    //noinspection ConstantConditions
                    getActivity().setTitle(title);

                    final StringBuilder sb = new StringBuilder("<table>");
                    final String[] columnNames = cursor.getColumnNames();
                    sb.append("<tr>");
                    for (String column : columnNames) {
                        sb.append("<td><i>").append(column).append("</i></td>");
                    }
                    sb.append("</tr>");

                    int maxLines = MAX_LINES;
                    while (cursor.moveToNext() && maxLines > 0) {
                        final StringBuilder line = new StringBuilder();
                        for (int c = 0; c < cursor.getColumnCount(); c++) {
                            line.append("<td>").append(cursor.getString(c)).append("</td>");
                        }
                        sb.append("<tr>").append(line.toString()).append("</tr>");
                        maxLines--;
                    }
                    sb.append("</table>");

                    mVb.output.loadDataWithBaseURL(null, sb.toString(),
                                                   TEXT_HTML, UTF_8, null);
                }
            }
        } catch (@NonNull final Exception e) {
            //noinspection ConstantConditions
            getActivity().setTitle("");
            mVb.output.loadDataWithBaseURL(null, e.getLocalizedMessage(),
                                           TEXT_HTML, UTF_8, null);
        }
    }
}
