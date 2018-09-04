/*
 * @copyright 2011 Philip Warner
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
package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.debug.Logger;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class Utils {
    private Utils() {
    }

    public static final String APP_NAME = "Book Catalogue";
    public static final boolean USE_LT = true;
    public static final boolean USE_BARCODE = true;


    //public static final String APP_NAME = "DVD Catalogue";
    //public static final String LOCATION = "dvdCatalogue";
    //public static final String DATABASE_NAME = "dvd_catalogue";
    //public static final boolean USE_LT = false;
    //public static final boolean USE_BARCODE = false;
    //public static final String APP_NAME = "CD Catalogue";
    //public static final String LOCATION = "cdCatalogue";
    //public static final String DATABASE_NAME = "cd_catalogue";
    //public static final boolean USE_LT = true;
    //public static final boolean USE_BARCODE = false;

    /**
     * Given a InputStream, save it to a file.
     *
     * @param in  InputStream to read
     * @param out File to save
     *
     * @return true if successful
     */
    static public boolean saveInputToFile(InputStream in, File out) {
        File temp = null;
        boolean isOk = false;

        try {
            // Get a temp file to avoid overwriting output unless copy works
            temp = File.createTempFile("temp_", null, StorageUtils.getSharedStorage());
            FileOutputStream f = new FileOutputStream(temp);

            // Copy from input to temp file
            byte[] buffer = new byte[65536];
            int len1;
            while ((len1 = in.read(buffer)) >= 0) {
                f.write(buffer, 0, len1);
            }
            f.close();
            // All OK, so rename to real output file
            //noinspection ResultOfMethodCallIgnored
            temp.renameTo(out);
            isOk = true;
        } catch (IOException e) {
            Logger.logError(e);
        } finally {
            // Delete temp file if it still exists
            if (temp != null && temp.exists()) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                } catch (Exception ignored) {
                }
            }
        }
        return isOk;
    }

    /**
     * Utility routine to get the data from a URL. Makes sure timeout is set to avoid application
     * stalling.
     *
     * @param url URL to retrieve
     *
     * @return InputStream
     */
    static public InputStream getInputStream(URL url) throws UnknownHostException {

        synchronized (url) {

            int retries = 3;
            while (true) {
                try {
                    /*
                     * This is quite nasty; there seems to be a bug with URL.openConnection
                     *
                     * It CAN be reduced by doing the following:
                     *
                     *     ((HttpURLConnection)conn).setRequestMethod("GET");
                     *
                     * but I worry about future-proofing and the assumption that URL.openConnection
                     * will always return a HttpURLConnection. OFC, it probably will...until it doesn't.
                     *
                     * Using HttpClient and HttpGet explicitly seems to bypass the casting
                     * problem but still does not allow the timeouts to work, or only works intermittently.
                     *
                     * Finally, there is another problem with faild timeouts:
                     *
                     *     http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html
                     *
                     * So...we are forced to use a background thread to kill it.
                     */

                    // If at some stage in the future the casting code breaks...use the Apache one.
                    //final HttpClient client = new DefaultHttpClient();
                    //final HttpParams httpParameters = client.getParams();
                    //
                    //HttpConnectionParams.setConnectionTimeout(httpParameters, 30 * 1000);
                    //HttpConnectionParams.setSoTimeout        (httpParameters, 30 * 1000);
                    //
                    //final HttpGet conn = new HttpGet(url.toString());
                    //
                    //HttpResponse response = client.execute(conn);
                    //InputStream is = response.getEntity().getContent();
                    //return new BufferedInputStream(is);

                    final ConnectionInfo connInfo = new ConnectionInfo();

                    connInfo.conn = url.openConnection();
                    connInfo.conn.setUseCaches(false);
                    connInfo.conn.setDoInput(true);
                    connInfo.conn.setDoOutput(false);

                    HttpURLConnection c;
                    if (connInfo.conn instanceof HttpURLConnection) {
                        c = (HttpURLConnection) connInfo.conn;
                        c.setRequestMethod("GET");
                    } else {
                        c = null;
                    }

                    connInfo.conn.setConnectTimeout(30000);
                    connInfo.conn.setReadTimeout(30000);

                    Terminator.enqueue(new Runnable() {
                        @Override
                        public void run() {
                            if (connInfo.is != null) {
                                if (!connInfo.is.isClosed()) {
                                    try {
                                        connInfo.is.close();
                                        ((HttpURLConnection) connInfo.conn).disconnect();
                                    } catch (IOException e) {
                                        Logger.logError(e);
                                    }
                                }
                            } else {
                                ((HttpURLConnection) connInfo.conn).disconnect();
                            }

                        }
                    }, 30000);
                    connInfo.is = new StatefulBufferedInputStream(connInfo.conn.getInputStream());

                    if (c != null && c.getResponseCode() >= 300) {
                        Logger.logError(new RuntimeException("URL lookup failed: " + c.getResponseCode()
                                + " " + c.getResponseMessage() + ", URL: " + url.toString()));
                        return null;
                    }

                    return connInfo.is;

                } catch (java.net.UnknownHostException e) {
                    Logger.logError(e);
                    retries--;
                    if (retries-- == 0)
                        throw e;
                    try {
                        Thread.sleep(500);
                    } catch (Exception ignored) {
                    }
                } catch (Exception e) {
                    Logger.logError(e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /*
     *@return boolean return true if the application can access the internet
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if passed bundle contains a non-blank string at key k.
     *
     * @param b   Bundle to check
     * @param key Key to check for
     *
     * @return Present/absent
     */
    public static boolean isNonBlankString(Bundle b, String key) {
        if (b.containsKey(key)) {
            String s = b.getString(key);
            return (s != null && !s.isEmpty());
        } else {
            return false;
        }
    }

    /**
     * Passed a list of Objects, remove duplicates based on the toString result.
     * <p>
     * ENHANCE Add author_aliases table to allow further pruning (eg. Joe Haldeman == Jow W Haldeman).
     * ENHANCE Add series_aliases table to allow further pruning (eg. 'Amber Series' <==> 'Amber').
     *
     * @param db   Database connection to lookup IDs
     * @param list List to clean up
     */
    public static <T extends ItemWithIdFixup> boolean pruneList(CatalogueDBAdapter db, ArrayList<T> list) {
        Hashtable<String, Boolean> names = new Hashtable<>();
        Hashtable<Long, Boolean> ids = new Hashtable<>();

        // We have to go forwards through the list because 'first item' is important,
        // but we also can't delete things as we traverse if we are going forward. So
        // we build a list of items to delete.
        ArrayList<Integer> toDelete = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            T item = list.get(i);
            Long id = item.fixupId(db);
            String name = item.toString().trim().toUpperCase();

            // Series special case - same name different series number.
            // This means different series positions will have the same ID but will have
            // different names; so ItemWithIdFixup contains the 'isUniqueById()' method.
            if (ids.containsKey(id) && !names.containsKey(name) && !item.isUniqueById()) {
                ids.put(id, true);
                names.put(name, true);
            } else if (names.containsKey(name) || (id != 0 && ids.containsKey(id))) {
                toDelete.add(i);
            } else {
                ids.put(id, true);
                names.put(name, true);
            }
        }
        for (int i = toDelete.size() - 1; i >= 0; i--)
            list.remove(toDelete.get(i).intValue());
        return toDelete.size() > 0;
    }

    // Code removed in order to remove the temptation to USE it; proper-casing is very locale-specific.
//	/**
//	 * Convert text at specified key to proper case.
//	 * 
//	 * @param values
//	 * @param key
//	 */
//	public static void doProperCase(Bundle values, String key) {
//		if (!values.containsKey(key))
//			return;
//		values.putString(key, properCase(values.getString(key)));
//	}
//
//	public static String properCase(String inputString) {
//		StringBuilder ff = new StringBuilder(); 
//		String outputString;
//		int wordnum = 0;
//
//		try {
//			for(String f: inputString.split(" ")) {
//				if(ff.length() > 0) { 
//					ff.append(" "); 
//				} 
//				wordnum++;
//				String word = f.toLowerCase();
//	
//				if (word.substring(0,1).matches("[\"\\(\\./\\\\,]")) {
//					wordnum = 1;
//					ff.append(word.substring(0,1));
//					word = word.substring(1,word.length());
//				}
//	
//				/* Do not convert 1st char to uppercase in the following situations */
//				if (wordnum > 1 && word.matches("a|to|at|the|in|and|is|von|de|le")) {
//					ff.append(word);
//					continue;
//				} 
//				try {
//					if (word.substring(0,2).equals("mc")) {
//						ff.append(word.substring(0,1).toUpperCase());
//						ff.append(word.substring(1,2));
//						ff.append(word.substring(2,3).toUpperCase());
//						ff.append(word.substring(3,word.length()));
//						continue;
//					}
//				} catch (StringIndexOutOfBoundsException e) {
//					// do nothing and continue;
//				}
//	
//				try {
//					if (word.substring(0,3).equals("mac")) {
//						ff.append(word.substring(0,1).toUpperCase());
//						ff.append(word.substring(1,3));
//						ff.append(word.substring(3,4).toUpperCase());
//						ff.append(word.substring(4,word.length()));
//						continue;
//					}
//				} catch (StringIndexOutOfBoundsException e) {
//					// do nothing and continue;
//				}
//	
//				try {
//					ff.append(word.substring(0,1).toUpperCase());
//					ff.append(word.substring(1,word.length()));
//				} catch (StringIndexOutOfBoundsException e) {
//					ff.append(word);
//				}
//			}
//	
//			/* output */ 
//			outputString = ff.toString();
//		} catch (StringIndexOutOfBoundsException e) {
//			//empty string - do nothing
//			outputString = inputString;
//		}
//		return outputString;
//	}

    /**
     * Remove series from the list where the names are the same, but one entry has a null or empty position.
     * eg. the followig list should be processed as indicated:
     * <p>
     * fred(5)
     * fred <-- delete
     * bill <-- delete
     * bill <-- delete
     * bill(1)
     */
    public static boolean pruneSeriesList(ArrayList<Series> list) {
        ArrayList<Series> toDelete = new ArrayList<>();
        Hashtable<String, Series> index = new Hashtable<>();

        for (Series s : list) {
            final boolean emptyNum = s.number == null || s.number.trim().isEmpty();
            final String lcName = s.name.trim().toLowerCase();
            final boolean inNames = index.containsKey(lcName);
            if (!inNames) {
                // Just add and continue
                index.put(lcName, s);
            } else {
                // See if we can purge either
                if (emptyNum) {
                    // Always delete series with empty numbers if an equally or more specific one exists
                    toDelete.add(s);
                } else {
                    // See if the one in 'index' also has a num
                    Series orig = index.get(lcName);
                    if (orig.number == null || orig.number.trim().isEmpty()) {
                        // Replace with this one, and mark orig for delete
                        index.put(lcName, s);
                        toDelete.add(orig);
                    } else {
                        // Both have numbers. See if they are the same.
                        if (s.number.trim().toLowerCase().equals(orig.number.trim().toLowerCase())) {
                            // Same exact series, delete this one
                            toDelete.add(s);
                        } //else {
                        // Nothing to do: this is a different series position
                        //}
                    }
                }
            }
        }

        for (Series s : toDelete)
            list.remove(s);

        return (toDelete.size() > 0);

    }

    // TODO: Make sure all URL getters use this if possible.
    static public void parseUrlOutput(String path, SAXParserFactory factory, DefaultHandler handler) {
        SAXParser parser;
        URL url;

        try {
            url = new URL(path);
            parser = factory.newSAXParser();
            parser.parse(Utils.getInputStream(url), handler);
            // Don't bother catching general exceptions, they will be caught by the caller.
        } catch (ParserConfigurationException | IOException | SAXException e) {
            String s = "unknown";
            try {
                s = e.getMessage();
            } catch (Exception ignored) {
            }
            Logger.logError(e, s);
        }
    }

    /**
     * Linkify partial HTML. Linkify methods remove all spans before building links, this
     * method preserves them.
     * <p>
     * See: http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml
     *
     * @param html        Partial HTML
     * @param linkifyMask Linkify mask to use in Linkify.addLinks
     *
     * @return Spannable with all links
     */
    public static Spannable linkifyHtml(String html, int linkifyMask) {
        // Get the spannable HTML
        Spanned text = Html.fromHtml(html);
        // Save the span details for later restoration
        URLSpan[] currentSpans = text.getSpans(0, text.length(), URLSpan.class);

        // Build an empty spannable then add the links
        SpannableString buffer = new SpannableString(text);
        Linkify.addLinks(buffer, linkifyMask);

        // Add back the HTML spannables
        for (URLSpan span : currentSpans) {
            int end = text.getSpanEnd(span);
            int start = text.getSpanStart(span);
            buffer.setSpan(span, start, end, 0);
        }
        return buffer;
    }

    @SuppressWarnings("unused")
    public static void printStackTrace() {
        StackTraceElement[] all = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : all) {
            System.out.println(element.toString());
        }
    }


    public interface ItemWithIdFixup {
        long fixupId(CatalogueDBAdapter db);

        long getId();

        boolean isUniqueById();
    }

    /*
     * Check if phone has a network connection
     *
     * @return
     */
	/*
	public static boolean isOnline(Context ctx) {
	    ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo netInfo = cm.getActiveNetworkInfo();
	    if (netInfo != null && netInfo.isConnectedOrConnecting()) {
	        return true;
	    }
	    return false;
	}
	*/

    /*
     * Check if phone can connect to a specific host.
     * Does not work....
     *
     * ENHANCE: Find a way to make network host checks possible
     */
	/*
	public static boolean hostIsAvailable(Context ctx, String host) {
		if (!isOnline(ctx))
			return false;
		int addr;
		try {
			addr = lookupHost(host);			
		} catch (Exception e) {
			return false;
		}
	    ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
	    try {
		    return cm.requestRouteToHost(ConnectivityManager., addr);	    	
		} catch (Exception e) {
			return false;
		}
	}
	*/

//	public static int lookupHost(String hostname) {
//	    InetAddress inetAddress;
//	    try {
//	        inetAddress = InetAddress.getByName(hostname);
//	    } catch (UnknownHostException e) {
//	        return -1;
//	    }
//	    byte[] addrBytes;
//	    int addr;
//	    addrBytes = inetAddress.getAddress();
//	    addr = ((addrBytes[3] & 0xff) << 24)
//	            | ((addrBytes[2] & 0xff) << 16)
//	            | ((addrBytes[1] & 0xff) << 8)
//	            |  (addrBytes[0] & 0xff);
//	    return addr;
//	}

    private static class ConnectionInfo {
        URLConnection conn = null;
        StatefulBufferedInputStream is = null;
    }

    public static class StatefulBufferedInputStream extends BufferedInputStream implements Closeable{
        private boolean mIsClosed = false;

        StatefulBufferedInputStream(InputStream in) {
            super(in);
        }
//		public StatefulBufferedInputStream(InputStream in, int i) {
//			super(in, i);
//		}

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                mIsClosed = true;
            }
        }

        public boolean isClosed() {
            return mIsClosed;
        }
    }
}

