package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Class to handle export in a separate thread.
 *
 * @author Philip Warner
 */
public class ExportThread extends ManagedTask {

    /** backup copies to keep */
    private static final int COPIES = 5;

    private final Exporter.ExportListener mOnExportListener = new Exporter.ExportListener() {
        @Override
        public void onProgress(@NonNull final String message, final int position) {
            if (position > 0) {
                mManager.doProgress(ExportThread.this, message, position);
            } else {
                mManager.doProgress(message);
            }
        }

        @Override
        public boolean isCancelled() {
            return ExportThread.this.isCancelled();
        }

        @Override
        public void setMax(final int max) {
            mManager.setMax(ExportThread.this, max);
        }

    };
    private CatalogueDBAdapter mDb;

    public ExportThread(@NonNull final TaskManager manager) {
        super(manager);
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        mDb.open();
    }

    @Override
    protected void onRun() {
        if (StorageUtils.isWriteProtected()) {
            mManager.doToast("Export Failed - Could not write to external storage");
            return;
        }
        try {
            final FileOutputStream out = new FileOutputStream(StorageUtils.getTempExportFile());
            new CsvExporter().export(out, mOnExportListener, Exporter.EXPORT_ALL, null);

            if (out.getChannel().isOpen()) {
                out.close();
            }
            renameFiles();
        } catch (IOException e) {
            Logger.logError(e);
            mManager.doToast(getString(R.string.export_failed_sdcard));
        }

        /* was commented out in version 5.2.2.
           not removed, but moved to a private method (untested obviously) so it's more readable
           Also see {@link ExportThread}
         */

        //someThingOnRunUsedToDo();
    }

    @Override
    protected void onThreadFinish() {
        cleanup();
    }

    @Override
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }

    private void cleanup() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    /**
     * Backup the current file
     */
    private void renameFiles() {
        final File temp = StorageUtils.getTempExportFile();
        if (isCancelled()) {
            StorageUtils.deleteFile(temp);
        } else {
            String fmt = "export.%s.csv";
            File fLast = StorageUtils.getFile(String.format(fmt, COPIES));
             StorageUtils.deleteFile(fLast);

            for (int i = COPIES - 1; i > 0; i--) {
                final File fCurr = StorageUtils.getFile(String.format(fmt, i));
                StorageUtils.renameFile(fCurr, fLast);
                fLast = fCurr;
            }
            final File export = StorageUtils.getExportFile();
            StorageUtils.renameFile(export, fLast);
            StorageUtils.renameFile(temp, export);
        }
    }

//   private void someThingOnRunUsedToDo() {
//		mManager.doProgress(getString(R.string.export_starting_ellipsis));
//		boolean displayingStartupMessage = true;
//
//		StringBuilder export = new StringBuilder(
//			'"' + DatabaseDefinitions.KEY_ID + "\"," + 			//0
//			'"' + DatabaseDefinitions.BKEY_AUTHOR_DETAILS + "\"," + 	//2
//			'"' + DatabaseDefinitions.KEY_TITLE + "\"," + 			//4
//			'"' + DatabaseDefinitions.KEY_ISBN + "\"," + 			//5
//			'"' + DatabaseDefinitions.KEY_PUBLISHER + "\"," + 		//6
//			'"' + DatabaseDefinitions.KEY_BOOK_DATE_PUBLISHED + "\"," + 	//7
//			'"' + DatabaseDefinitions.KEY_RATING + "\"," + 			//8
//			'"' + "bookshelf_id\"," + 								//9
//			'"' + DatabaseDefinitions.KEY_BOOKSHELF + "\"," +		//10
//			'"' + DatabaseDefinitions.KEY_BOOK_READ + "\"," +				//11
//			'"' + DatabaseDefinitions.BKEY_SERIES_DETAILS + "\"," +	//12
//			'"' + DatabaseDefinitions.KEY_BOOK_PAGES + "\"," + 			//14
//			'"' + DatabaseDefinitions.KEY_NOTES + "\"," + 			//15
//			'"' + DatabaseDefinitions.KEY_BOOK_LIST_PRICE + "\"," + 		//16
//			'"' + DatabaseDefinitions.KEY_ANTHOLOGY_MASK+ "\"," + 		//17
//			'"' + DatabaseDefinitions.KEY_BOOK_LOCATION+ "\"," + 			//18
//			'"' + DatabaseDefinitions.KEY_BOOK_READ_START+ "\"," + 		//19
//			'"' + DatabaseDefinitions.KEY_BOOK_READ_END+ "\"," + 			//20
//			'"' + DatabaseDefinitions.KEY_BOOK_FORMAT+ "\"," + 			//21
//			'"' + DatabaseDefinitions.KEY_BOOK_SIGNED+ "\"," + 			//22
//			'"' + DatabaseDefinitions.KEY_LOANED_TO+ "\"," +			//23
//			'"' + "anthology_titles" + "\"," +						//24 
//			'"' + DatabaseDefinitions.KEY_DESCRIPTION+ "\"," + 		//25
//			'"' + DatabaseDefinitions.KEY_BOOK_GENRE+ "\"," + 			//26
//			'"' + DatabaseDefinitions.KEY_BOOK_DATE_ADDED+ "\"," + 		//27
//			'"' + DatabaseDefinitions.DOM_GOODREADS_BOOK_ID + "\"," + 		//28
//			'"' + DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE + "\"," + 		//29
//			'"' + DatabaseDefinitions.DOM_LAST_UPDATE_DATE + "\"," + 		//30
//			'"' + DatabaseDefinitions.DOM_BOOK_UUID + "\"," + 		//31
//			"\n");
//		
//		long lastUpdate = 0;
//		
//		StringBuilder row = new StringBuilder();
//		
//		BooksCursor books = mDb.exportBooks();
//		BooksRow rv = books.getRowView();
//
//		try {
//			final int totalBooks = books.getCount();
//
//			if (!isCancelled()) {
//	
//				mManager.setMax(this, totalBooks);
//
//				/* write to the SDCard */
//				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(StorageUtils.getFile(EXPORT_TEMP_FILE_NAME)), UTF8), BUFFER_SIZE);
//				out.write(export.toString());
//				if (books.moveToFirst()) {
//					do { 
//						num++;
//						long id = books.getLong(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_ID));
//						// Just get the string from the database and save it. It should be in standard SQL form already.
//						String dateString = "";
//						try {
//							dateString = books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_BOOK_DATE_PUBLISHED));
//						} catch (Exception e) {
//							//do nothing
//						}
//						// Just get the string from the database and save it. It should be in standard SQL form already.
//						String dateReadStartString = "";
//						try {
//							dateReadStartString = books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_BOOK_READ_START));
//						} catch (Exception e) {
//							Logger.logError(e);
//							//do nothing
//						}
//						// Just get the string from the database and save it. It should be in standard SQL form already.
//						String dateReadEndString = "";
//						try {
//							dateReadEndString = books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_BOOK_READ_END));
//						} catch (Exception e) {
//							Logger.logError(e);
//							//do nothing
//						}
//						// Just get the string from the database and save it. It should be in standard SQL form already.
//						String dateAddedString = "";
//						try {
//							dateAddedString = books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_BOOK_DATE_ADDED));
//						} catch (Exception e) {
//							//do nothing
//						}
//
//						String anthology = books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_ANTHOLOGY_MASK));
//						String anthology_titles = "";
//						if (anthology.equals(CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS + "") || anthology.equals(CatalogueDBAdapter.ANTHOLOGY_IS_ANTHOLOGY + "")) {
//							Cursor titles = mDb.fetchAnthologyTitlesByBook(id);
//							try {
//								if (titles.moveToFirst()) {
//									do { 
//										String anth_title = titles.getString(titles.getColumnIndexOrThrow(DatabaseDefinitions.KEY_TITLE));
//										String anth_author = titles.getString(titles.getColumnIndexOrThrow(DatabaseDefinitions.KEY_AUTHOR_NAME));
//										anthology_titles += anth_title + " " + AnthologyTitle.TITLE_AUTHOR_DELIM +" " + anth_author + ArrayUtils.MULTI_STRING_SEPARATOR;
//									} while (titles.moveToNext()); 
//								}
//							} finally {
//								if (titles != null)
//									titles.close();
//							}
//						}
//						String title = books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_TITLE));
//						//Display the selected bookshelves
//						Cursor bookshelves = mDb.fetchAllBookshelvesByBook(id);
//						String bookshelves_id_text = "";
//						String bookshelves_name_text = "";
//						while (bookshelves.moveToNext()) {
//							bookshelves_id_text += bookshelves.getString(bookshelves.getColumnIndex(DatabaseDefinitions.KEY_ID)) + BookEditFields.BOOKSHELF_SEPERATOR;
//							bookshelves_name_text += Utils.encodeListItem(bookshelves.getString(bookshelves.getColumnIndex(DatabaseDefinitions.KEY_BOOKSHELF)),EditBookFieldsFragment.MULTI_STRING_SEPARATOR) + EditBookFieldsFragment.MULTI_STRING_SEPARATOR;
//						}
//						bookshelves.close();
//
//						String authorDetails = Convert.encodeList( mDb.getBookAuthorList(id), '|' );
//						String seriesDetails = Convert.encodeList( mDb.getBookSeriesList(id), '|' );
//
//						row.setLength(0);
//						row.append("\"" + formatCell(id) + "\",");
//						row.append("\"" + formatCell(authorDetails) + "\",");
//						row.append( "\"" + formatCell(title) + "\"," );
//						row.append("\"" + formatCell(rv.getIsbn()) + "\",");
//						row.append("\"" + formatCell(rv.getPublisher()) + "\",");
//						row.append("\"" + formatCell(dateString) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_RATING))) + "\",");
//						row.append("\"" + formatCell(bookshelves_id_text) + "\",");
//						row.append("\"" + formatCell(bookshelves_name_text) + "\",");
//						row.append("\"" + formatCell(rv.isRead()) + "\",");
//						row.append("\"" + formatCell(seriesDetails) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_BOOK_PAGES))) + "\",");
//						row.append("\"" + formatCell(rv.getNotes()) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_BOOK_LIST_PRICE))) + "\",");
//						row.append("\"" + formatCell(anthology) + "\",");
//						row.append("\"" + formatCell(rv.getLocation()) + "\",");
//						row.append("\"" + formatCell(dateReadStartString) + "\",");
//						row.append("\"" + formatCell(dateReadEndString) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_BOOK_FORMAT))) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_BOOK_SIGNED))) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.KEY_LOANED_TO))+"") + "\",");
//						row.append("\"" + formatCell(anthology_titles) + "\",");
//						row.append("\"" + formatCell(rv.getDescription()) + "\",");
//						row.append("\"" + formatCell(rv.getGenre()) + "\",");
//						row.append("\"" + formatCell(dateAddedString) + "\",");
//						row.append("\"" + formatCell(rv.getGoodreadsBookId()) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE.name))) + "\",");
//						row.append("\"" + formatCell(books.getString(books.getColumnIndexOrThrow(DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name))) + "\",");
//						row.append("\"" + formatCell(rv.getBookUuid()) + "\",");
//						row.append("\n");
//						out.write(row.toString());
//						//export.append(row);
//						
//						long now = System.currentTimeMillis();
//						if ( (now - lastUpdate) > 200) {
//							if (displayingStartupMessage) {
//								mManager.doProgress("");
//								displayingStartupMessage = false;
//							}
//							doProgress(title, num);
//							lastUpdate = now;
//						}
//					}
//					while (books.moveToNext() && !isCancelled()); 
//				} 
//				
//				out.close();
//				//Toast.makeText(AdministrationFunctions.this, R.string.export_complete, Toast.LENGTH_LONG).show();
//				renameFiles();
//			}
//			
//		} catch (IOException e) {
//			Logger.logError(e);
//			mManager.doToast(getString(R.string.export_failed_sdcard));
//		} finally {
//			if (displayingStartupMessage) {
//				mManager.doProgress("");
//				displayingStartupMessage = false;
//			}
//			if (!isCancelled()) {
//				mManager.doToast( getString(R.string.export_complete) );
//			} else {
//				mManager.doToast( getString(R.string.cancelled) );				
//			}
//			if (books != null)
//				books.close();
//		}
//    }


//    /**
//     * Double quote all "'s and remove all newlines
//     *
//     * @param cell The cell the format
//     *
//     * @return The formatted cell
//     */
//    private String formatCell(long cell) {
//        String newcell = cell + "";
//        return formatCell(newcell);
//    }

//    /**
//     * Double quote all "'s and remove all newlines
//     *
//     * @param cell The cell the format
//     *
//     * @return The formatted cell
//     */
//    private String formatCell(String cell) {
//        try {
//            if (cell.equals("null") || cell.trim().isEmpty()) {
//                return "";
//            }
//            StringBuilder bld = new StringBuilder();
//            int endPos = cell.length() - 1;
//            int pos = 0;
//            while (pos <= endPos) {
//                char c = cell.charAt(pos);
//                switch (c) {
//                    case '\r':
//                        bld.append("\\r");
//                        break;
//                    case '\n':
//                        bld.append("\\n");
//                        break;
//                    case '\t':
//                        bld.append("\\t");
//                        break;
//                    case '"':
//                        bld.append("\"\"");
//                        break;
//                    case '\\':
//                        bld.append("\\\\");
//                        break;
//                    default:
//                        bld.append(c);
//                }
//                pos++;
//
//            }
//            return bld.toString();
//        } catch (NullPointerException e) {
//            return "";
//        }
//    }
}