/*
 * Copyright (C) 2015 Federico Iosue (federico.iosue@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.feio.android.omninotes.db;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteReadOnlyDatabaseException;
import android.net.Uri;
import android.util.Log;
import it.feio.android.omninotes.OmniNotes;
import it.feio.android.omninotes.async.upgrade.UpgradeProcessor;
import it.feio.android.omninotes.models.*;
import it.feio.android.omninotes.utils.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.Pattern;


public class DbHelper extends SQLiteOpenHelper {

    // Database name
    private static final String DATABASE_NAME = Constants.DATABASE_NAME;
    // Database version aligned if possible to software version
    private static final int DATABASE_VERSION = 502;
    // Sql query file directory
    private static final String SQL_DIR = "sql";

    // Notes table name
    public static final String TABLE_NOTES = "notes";
    // Notes table columns
    public static final String KEY_ID = "creation";
    public static final String KEY_CREATION = "creation";
    public static final String KEY_LAST_MODIFICATION = "last_modification";
    public static final String KEY_TITLE = "title";
    public static final String KEY_CONTENT = "content";
    public static final String KEY_ARCHIVED = "archived";
    public static final String KEY_TRASHED = "trashed";
    public static final String KEY_REMINDER = "alarm";
    public static final String KEY_REMINDER_FIRED = "reminder_fired";
    public static final String KEY_RECURRENCE_RULE = "recurrence_rule";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_CATEGORY = "category_id";
    public static final String KEY_LOCKED = "locked";
    public static final String KEY_CHECKLIST = "checklist";

    // Attachments table name
    public static final String TABLE_ATTACHMENTS = "attachments";
    // Attachments table columns
    public static final String KEY_ATTACHMENT_ID = "attachment_id";
    public static final String KEY_ATTACHMENT_URI = "uri";
    public static final String KEY_ATTACHMENT_NAME = "name";
    public static final String KEY_ATTACHMENT_SIZE = "size";
    public static final String KEY_ATTACHMENT_LENGTH = "length";
    public static final String KEY_ATTACHMENT_MIME_TYPE = "mime_type";
    public static final String KEY_ATTACHMENT_NOTE_ID = "note_id";

    // Categories table name
    public static final String TABLE_CATEGORY = "categories";
    // Categories table columns
    public static final String KEY_CATEGORY_ID = "category_id";
    public static final String KEY_CATEGORY_NAME = "name";
    public static final String KEY_CATEGORY_DESCRIPTION = "description";
    public static final String KEY_CATEGORY_COLOR = "color";

    // Queries
    private static final String CREATE_QUERY = "create.sql";
    private static final String UPGRADE_QUERY_PREFIX = "upgrade-";
    private static final String UPGRADE_QUERY_SUFFIX = ".sql";


    private final Context mContext;
    private final SharedPreferences prefs;

    private static DbHelper instance = null;
	private SQLiteDatabase db;


	public static synchronized DbHelper getInstance() {
		return getInstance(OmniNotes.getAppContext());
	}
	

	public static synchronized DbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DbHelper(context);
        }
        return instance;
    }


	public static synchronized DbHelper getInstance(boolean forcedNewInstance) {
		if (instance == null || forcedNewInstance) {
			instance = new DbHelper(OmniNotes.getAppContext());
		}
		return instance;
	}


    private DbHelper(Context mContext) {
        super(mContext, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = mContext;
        this.prefs = mContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_MULTI_PROCESS);
    }


    public String getDatabaseName() {
        return DATABASE_NAME;
    }

	public SQLiteDatabase getDatabase() {
		return getDatabase(false);
	}


	public SQLiteDatabase getDatabase(boolean forceWritable) {
		try {
			SQLiteDatabase db = getReadableDatabase();
			if (forceWritable && db.isReadOnly()) {
				throw new SQLiteReadOnlyDatabaseException("Required writable database, obtained read-only");
			}
			return db;
		} catch (IllegalStateException e) {
			return this.db;
		}
	}


    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            Log.i(Constants.TAG, "Database creation");
            execSqlFile(CREATE_QUERY, db);
        } catch (IOException exception) {
            throw new RuntimeException("Database creation failed", exception);
        }
    }


    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		this.db = db;
        Log.i(Constants.TAG, "Upgrading database version from " + oldVersion + " to " + newVersion);

        try {

            UpgradeProcessor.process(oldVersion, newVersion);

            for (String sqlFile : AssetUtils.list(SQL_DIR, mContext.getAssets())) {
                if (sqlFile.startsWith(UPGRADE_QUERY_PREFIX)) {
                    int fileVersion = Integer.parseInt(sqlFile.substring(UPGRADE_QUERY_PREFIX.length(),
                            sqlFile.length() - UPGRADE_QUERY_SUFFIX.length()));
                    if (fileVersion > oldVersion && fileVersion <= newVersion) {
                        execSqlFile(sqlFile, db);
                    }
                }
            }
            Log.i(Constants.TAG, "Database upgrade successful");

        } catch (IOException |InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Database upgrade failed", e);
        }
    }


    // Inserting or updating single note
    public Note updateNote(Note note, boolean updateLastModification) {
        SQLiteDatabase db = getDatabase(true);

        String content;
        if (note.isLocked()) {
            content = Security.encrypt(note.getContent(), prefs.getString(Constants.PREF_PASSWORD, ""));
        } else {
            content = note.getContent();
        }

        // To ensure note and attachments insertions are atomical and boost performances transaction are used
        db.beginTransaction();

        ContentValues values = new ContentValues();
        values.put(KEY_TITLE, note.getTitle());
        values.put(KEY_CONTENT, content);
        values.put(KEY_CREATION, note.getCreation() != null ? note.getCreation() : Calendar.getInstance()
                .getTimeInMillis());
        values.put(KEY_LAST_MODIFICATION, updateLastModification ? Calendar
                .getInstance().getTimeInMillis() : (note.getLastModification() != null ? note.getLastModification() :
                Calendar
                        .getInstance().getTimeInMillis()));
        values.put(KEY_ARCHIVED, note.isArchived());
        values.put(KEY_TRASHED, note.isTrashed());
        values.put(KEY_REMINDER, note.getAlarm());
        values.put(KEY_REMINDER_FIRED, note.isReminderFired());
        values.put(KEY_RECURRENCE_RULE, note.getRecurrenceRule());
        values.put(KEY_LATITUDE, note.getLatitude());
        values.put(KEY_LONGITUDE, note.getLongitude());
        values.put(KEY_ADDRESS, note.getAddress());
        values.put(KEY_CATEGORY, note.getCategory() != null ? note.getCategory().getId() : null);
        boolean locked = note.isLocked() != null ? note.isLocked() : false;
        values.put(KEY_LOCKED, locked);
        boolean checklist = note.isChecklist() != null ? note.isChecklist() : false;
        values.put(KEY_CHECKLIST, checklist);

		db.insertWithOnConflict(TABLE_NOTES, KEY_ID, values, SQLiteDatabase.CONFLICT_REPLACE);
		Log.d(Constants.TAG, "Updated note titled '" + note.getTitle() + "'");

        // Updating attachments
        List<Attachment> deletedAttachments = note.getAttachmentsListOld();
        for (Attachment attachment : note.getAttachmentsList()) {
			updateAttachment(note.get_id() != null ? note.get_id() : values.getAsLong(KEY_CREATION), attachment, db);
			deletedAttachments.remove(attachment);
        }
        // Remove from database deleted attachments
        for (Attachment attachmentDeleted : deletedAttachments) {
            db.delete(TABLE_ATTACHMENTS, KEY_ATTACHMENT_ID + " = ?",
                    new String[]{String.valueOf(attachmentDeleted.getId())});
        }

        db.setTransactionSuccessful();
        db.endTransaction();
		db.close();

        // Fill the note with correct data before returning it
        note.setCreation(note.getCreation() != null ? note.getCreation() : values.getAsLong(KEY_CREATION));
        note.setLastModification(values.getAsLong(KEY_LAST_MODIFICATION));

        return note;
    }


    protected void execSqlFile(String sqlFile, SQLiteDatabase db) throws SQLException, IOException {
        Log.i(Constants.TAG, "  exec sql file: {}" + sqlFile);
        for (String sqlInstruction : SqlParser.parseSqlFile(SQL_DIR + "/" + sqlFile, mContext.getAssets())) {
            Log.v(Constants.TAG, "    sql: {}" + sqlInstruction);
            try {
                db.execSQL(sqlInstruction);
            } catch (Exception e) {
                Log.e(Constants.TAG, "Error executing command: " + sqlInstruction, e);
            }
        }
    }


    /**
     * Attachments update
     * */
    public Attachment updateAttachment(Attachment attachment) {
        return updateAttachment(-1, attachment, getDatabase(true));
    }


    /**
     * New attachment insertion
     * */
    public Attachment updateAttachment(long noteId, Attachment attachment, SQLiteDatabase db) {
        ContentValues valuesAttachments = new ContentValues();
		valuesAttachments.put(KEY_ATTACHMENT_ID, attachment.getId() != null ? attachment.getId() : Calendar
				.getInstance().getTimeInMillis());
		valuesAttachments.put(KEY_ATTACHMENT_NOTE_ID, noteId);
        valuesAttachments.put(KEY_ATTACHMENT_URI, attachment.getUri().toString());
        valuesAttachments.put(KEY_ATTACHMENT_MIME_TYPE, attachment.getMime_type());
        valuesAttachments.put(KEY_ATTACHMENT_NAME, attachment.getName());
        valuesAttachments.put(KEY_ATTACHMENT_SIZE, attachment.getSize());
        valuesAttachments.put(KEY_ATTACHMENT_LENGTH, attachment.getLength());
		db.insertWithOnConflict(TABLE_ATTACHMENTS, KEY_ATTACHMENT_ID, valuesAttachments, SQLiteDatabase.CONFLICT_REPLACE);
        return attachment;
    }


    /**
     * Getting single note
     */
    public Note getNote(long id) {

        String whereCondition = " WHERE "
                + KEY_ID + " = " + id;

        List<Note> notes = getNotes(whereCondition, true);
        Note note;
        if (notes.size() > 0) {
            note = notes.get(0);
        } else {
            note = null;
        }
        return note;
    }


    /**
     * Getting All notes
     *
     * @param checkNavigation Tells if navigation status (notes, archived) must be kept in
     *                        consideration or if all notes have to be retrieved
     * @return Notes list
     */
    public List<Note> getAllNotes(Boolean checkNavigation) {
        String whereCondition = "";
        if (checkNavigation) {
            int navigation = Navigation.getNavigation();
            switch (navigation) {
                case Navigation.NOTES:
                    return getNotesActive();
                case Navigation.ARCHIVE:
                    return getNotesArchived();
                case Navigation.REMINDERS:
                    return getNotesWithReminder(prefs.getBoolean(Constants.PREF_FILTER_PAST_REMINDERS, false));
                case Navigation.TRASH:
                    return getNotesTrashed();
                case Navigation.UNCATEGORIZED:
                    return getNotesUncategorized();
                case Navigation.CATEGORY:
                    return getNotesByCategory(Navigation.getCategory());
                default:
                    return getNotes(whereCondition, true);
            }
        } else {
            return getNotes(whereCondition, true);
        }

    }


    public List<Note> getNotesActive() {
        String whereCondition = " WHERE " + KEY_ARCHIVED + " IS NOT 1 AND " + KEY_TRASHED + " IS NOT 1 ";
        return getNotes(whereCondition, true);
    }


    public List<Note> getNotesArchived() {
        String whereCondition = " WHERE " + KEY_ARCHIVED + " = 1 AND " + KEY_TRASHED + " IS NOT 1 ";
        return getNotes(whereCondition, true);
    }


    public List<Note> getNotesTrashed() {
        String whereCondition = " WHERE " + KEY_TRASHED + " = 1 ";
        return getNotes(whereCondition, true);
    }


    public List<Note> getNotesUncategorized() {
        String whereCondition = " WHERE "
                + "(" + KEY_CATEGORY_ID + " IS NULL OR " + KEY_CATEGORY_ID + " == 0) "
                + "AND " + KEY_TRASHED + " IS NOT 1";
        return getNotes(whereCondition, true);
    }


    public List<Note> getNotesWithLocation() {
        String whereCondition = " WHERE " + KEY_LONGITUDE + " IS NOT NULL "
                + "AND " + KEY_LONGITUDE + " != 0 ";
        return getNotes(whereCondition, true);
    }


    /**
     * Counts words in a note
     */
    public int getWords(Note note) {
        int count = 0;
        String[] fields = {note.getTitle(), note.getContent()};
        for (String field : fields) {
            boolean word = false;
            int endOfLine = field.length() - 1;
            for (int i = 0; i < field.length(); i++) {
                // if the char is a letter, word = true.
                if (Character.isLetter(field.charAt(i)) && i != endOfLine) {
                    word = true;
                    // if char isn't a letter and there have been letters before,
                    // counter goes up.
                } else if (!Character.isLetter(field.charAt(i)) && word) {
                    count++;
                    word = false;
                    // last word of String; if it doesn't end with a non letter, it
                    // wouldn't count without this.
                } else if (Character.isLetter(field.charAt(i)) && i == endOfLine) {
                    count++;
                }
            }
        }
        return count;
    }


    /**
     * Counts chars in a note
     */
    public int getChars(Note note) {
        int count = 0;
        count += note.getTitle().length();
        count += note.getContent().length();
        return count;
    }


    /**
     * Common method for notes retrieval. It accepts a query to perform and returns matching records.
     */
    public List<Note> getNotes(String whereCondition, boolean order) {
        List<Note> noteList = new ArrayList<>();

        String sort_column, sort_order = "";

        // Getting sorting criteria from preferences. Reminder screen forces sorting.
        if (Navigation.checkNavigation(Navigation.REMINDERS)) {
            sort_column = KEY_REMINDER;
        } else {
            sort_column = prefs.getString(Constants.PREF_SORTING_COLUMN, KEY_TITLE);
        }
        if (order) {
            sort_order = KEY_TITLE.equals(sort_column) || KEY_REMINDER.equals(sort_column) ? " ASC " : " DESC ";
        }

        // In case of title sorting criteria it must be handled empty title by concatenating content
        sort_column = KEY_TITLE.equals(sort_column) ? KEY_TITLE + "||" + KEY_CONTENT : sort_column;

        // In case of reminder sorting criteria the empty reminder notes must be moved on bottom of results
        sort_column = KEY_REMINDER.equals(sort_column) ? "IFNULL(" + KEY_REMINDER + ", " +
                "" + Constants.TIMESTAMP_UNIX_EPOCH + ")" : sort_column;

        // Generic query to be specialized with conditions passed as parameter
        String query = "SELECT "
                + KEY_CREATION + ","
                + KEY_LAST_MODIFICATION + ","
                + KEY_TITLE + ","
                + KEY_CONTENT + ","
                + KEY_ARCHIVED + ","
                + KEY_TRASHED + ","
                + KEY_REMINDER + ","
                + KEY_REMINDER_FIRED + ","
                + KEY_RECURRENCE_RULE + ","
                + KEY_LATITUDE + ","
                + KEY_LONGITUDE + ","
                + KEY_ADDRESS + ","
                + KEY_LOCKED + ","
                + KEY_CHECKLIST + ","
                + KEY_CATEGORY + ","
                + KEY_CATEGORY_NAME + ","
                + KEY_CATEGORY_DESCRIPTION + ","
                + KEY_CATEGORY_COLOR
                + " FROM " + TABLE_NOTES
                + " LEFT JOIN " + TABLE_CATEGORY + " USING( " + KEY_CATEGORY + ") "
                + whereCondition
                + (order ? " ORDER BY " + sort_column + sort_order : "");

        Log.v(Constants.TAG, "Query: " + query);

        Cursor cursor = null;
        try {
            cursor = getDatabase().rawQuery(query, null);

            // Looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    int i = 0;
                    Note note = new Note();
                    note.setCreation(cursor.getLong(i++));
                    note.setLastModification(cursor.getLong(i++));
                    note.setTitle(cursor.getString(i++));
                    note.setContent(cursor.getString(i++));
                    note.setArchived("1".equals(cursor.getString(i++)));
                    note.setTrashed("1".equals(cursor.getString(i++)));
                    note.setAlarm(cursor.getString(i++));
                    note.setReminderFired(cursor.getInt(i++));
                    note.setRecurrenceRule(cursor.getString(i++));
                    note.setLatitude(cursor.getString(i++));
                    note.setLongitude(cursor.getString(i++));
                    note.setAddress(cursor.getString(i++));
                    note.setLocked("1".equals(cursor.getString(i++)));
                    note.setChecklist("1".equals(cursor.getString(i++)));

                    // Eventual decryption of content
                    if (note.isLocked()) {
                        note.setContent(Security.decrypt(note.getContent(), prefs.getString(Constants.PREF_PASSWORD,
                                "")));
                    }

                    // Set category
					long categoryId = cursor.getLong(i++);
					if (categoryId != 0) {
						Category category = new Category(categoryId, cursor.getString(i++),
								cursor.getString(i++), cursor.getString(i++));
						note.setCategory(category);
					}

                    // Add eventual attachments uri
                    note.setAttachmentsList(getNoteAttachments(note));

                    // Adding note to list
                    noteList.add(note);

                } while (cursor.moveToNext());
            }

        } finally {
            if (cursor != null)
                cursor.close();
        }

        Log.v(Constants.TAG, "Query: Retrieval finished!");
        return noteList;
    }


    /**
     * Archives/restore single note
     */
    public void archiveNote(Note note, boolean archive) {
        note.setArchived(archive);
        updateNote(note, false);
    }


    /**
     * Trashes/restore single note
     */
    public void trashNote(Note note, boolean trash) {
        note.setTrashed(trash);
        updateNote(note, false);
    }


    /**
     * Deleting single note
     */
    public boolean deleteNote(Note note) {
        return deleteNote(note, false);
    }


	/**
	 * Deleting single note, eventually keeping attachments
	 */
	public boolean deleteNote(Note note, boolean keepAttachments) {
		return deleteNote(note.get_id(), keepAttachments);
	}


	/**
	 * Deleting single note by its id
	 */
	public boolean deleteNote(long noteId, boolean keepAttachments) {
		SQLiteDatabase db = getDatabase(true);
		db.delete(TABLE_NOTES, KEY_ID + " = ?", new String[]{String.valueOf(noteId)});
		if (!keepAttachments) {
			db.delete(TABLE_ATTACHMENTS, KEY_ATTACHMENT_NOTE_ID + " = ?", new String[]{String.valueOf(noteId)});
		}
		return true;
	}


    /**
     * Empties trash deleting all trashed notes
     */
    public void emptyTrash() {
        for (Note note : getNotesTrashed()) {
            deleteNote(note);
        }
    }


    /**
     * Gets notes matching pattern with title or content text
     *
     * @param pattern String to match with
     * @return Notes list
     */
    public List<Note> getNotesByPattern(String pattern) {
        int navigation = Navigation.getNavigation();
        String whereCondition = " WHERE "
                + KEY_TRASHED + (navigation == Navigation.TRASH ? " IS 1" : " IS NOT 1")
                + (navigation == Navigation.ARCHIVE ? " AND " + KEY_ARCHIVED + " IS 1" : "")
                + (navigation == Navigation.CATEGORY ? " AND " + KEY_CATEGORY + " = " + Navigation.getCategory() : "")
                + (navigation == Navigation.UNCATEGORIZED ? " AND (" + KEY_CATEGORY + " IS NULL OR " + KEY_CATEGORY_ID
                + " == 0) " : "")
                + (Navigation.checkNavigation(Navigation.REMINDERS) ? " AND " + KEY_REMINDER + " IS NOT NULL" : "")
                + " AND ("
                + " ( " + KEY_LOCKED + " IS NOT 1 AND (" + KEY_TITLE + " LIKE '%" + pattern + "%' " + " OR " +
                KEY_CONTENT + " LIKE '%" + pattern + "%' ))"
                + " OR ( " + KEY_LOCKED + " = 1 AND " + KEY_TITLE + " LIKE '%" + pattern + "%' )"
                + ")";
        return getNotes(whereCondition, true);
    }


    /**
     * Search for notes with reminder
     *
     * @param filterPastReminders Excludes past reminders
     * @return Notes list
     */
    public List<Note> getNotesWithReminder(boolean filterPastReminders) {
        String whereCondition = " WHERE " + KEY_REMINDER
                + (filterPastReminders ? " >= " + Calendar.getInstance().getTimeInMillis() : " IS NOT NULL")
                + " AND " + KEY_ARCHIVED + " IS NOT 1"
                + " AND " + KEY_TRASHED + " IS NOT 1";
        return getNotes(whereCondition, true);
    }


    /**
     * Returns all notes that have a reminder that has not been alredy fired
     *
     * @return Notes list
     */
    public List<Note> getNotesWithReminderNotFired () {
        String whereCondition = " WHERE " + KEY_REMINDER + " IS NOT NULL"
                                + " AND " + KEY_REMINDER_FIRED + " IS NOT 1"
                                + " AND " + KEY_ARCHIVED + " IS NOT 1"
                                + " AND " + KEY_TRASHED + " IS NOT 1";
        return getNotes(whereCondition, true);
    }


    /**
     * Retrieves locked or unlocked notes
     */
    public List<Note> getNotesWithLock(boolean locked) {
        String whereCondition = " WHERE " + KEY_LOCKED + (locked ? " = 1 " : " IS NOT 1 ");
        return getNotes(whereCondition, true);
    }


    /**
     * Search for notes with reminder expiring the current day
     *
     * @return Notes list
     */
    public List<Note> getTodayReminders() {
        String whereCondition = " WHERE DATE(" + KEY_REMINDER + "/1000, 'unixepoch') = DATE('now') AND " +
                KEY_TRASHED + " IS NOT 1";
        return getNotes(whereCondition, false);
    }


    /**
     * Retrieves all attachments related to specific note
     */
    public ArrayList<Attachment> getNoteAttachments(Note note) {
        String whereCondition = " WHERE " + KEY_ATTACHMENT_NOTE_ID + " = " + note.get_id();
        return getAttachments(whereCondition);
    }


    public List<Note> getChecklists() {
        String whereCondition = " WHERE " + KEY_CHECKLIST + " = 1";
        return getNotes(whereCondition, false);
    }


    public List<Note> getMasked() {
        String whereCondition = " WHERE " + KEY_LOCKED + " = 1";
        return getNotes(whereCondition, false);
    }


    /**
     * Retrieves all notes related to Category it passed as parameter
     *
     * @param categoryId Category integer identifier
     * @return List of notes with requested category
     */
    public List<Note> getNotesByCategory(Long categoryId) {
        List<Note> notes;
		boolean filterArchived = prefs.getBoolean(Constants.PREF_FILTER_ARCHIVED_IN_CATEGORIES + categoryId, false);
        try {
            String whereCondition = " WHERE "
                    + KEY_CATEGORY_ID + " = " + categoryId
					+ " AND " + KEY_TRASHED + " IS NOT 1"
					+ (filterArchived ? " AND " + KEY_ARCHIVED + " IS NOT 1" : "");
			notes = getNotes(whereCondition, true);
        } catch (NumberFormatException e) {
            notes = getAllNotes(true);
        }
        return notes;
    }


    /**
     * Retrieves all tags
     */
    public List<Tag> getTags() {
        return getTags(null);
    }


    /**
     * Retrieves all tags of a specified note
     */
    public List<Tag> getTags(Note note) {
        List<Tag> tags = new ArrayList<>();
        HashMap<String, Integer> tagsMap = new HashMap<>();

        String whereCondition = " WHERE "
                + (note != null ? KEY_ID + " = " + note.get_id() + " AND " : "")
                + "(" + KEY_CONTENT + " LIKE '%#%' OR " + KEY_TITLE + " LIKE '%#%' " + ")"
                + " AND " + KEY_TRASHED + " IS " + (Navigation.checkNavigation(Navigation.TRASH) ? "" : " NOT ") + " 1";
        List<Note> notesRetrieved = getNotes(whereCondition, true);

        for (Note noteRetrieved : notesRetrieved) {
            HashMap<String, Integer> tagsRetrieved = TagsHelper.retrieveTags(noteRetrieved);
            for (String s : tagsRetrieved.keySet()) {
                int count = tagsMap.get(s) == null ? 0 : tagsMap.get(s);
                tagsMap.put(s, ++count);
            }
        }

        for (String s : tagsMap.keySet()) {
            Tag tag = new Tag(s, tagsMap.get(s));
            tags.add(tag);
        }

        Collections.sort(tags, (tag1, tag2) -> tag1.getText().compareToIgnoreCase(tag2.getText()));
        return tags;
    }


    /**
     * Retrieves all notes related to category it passed as parameter
     */
    public List<Note> getNotesByTag(String tag) {
        if (tag.contains(",")) {
            return getNotesByTag(tag.split(","));
        } else {
            return getNotesByTag(new String[]{tag});
        }
    }


    /**
     * Retrieves all notes with specified tags
     */
    public List<Note> getNotesByTag(String[] tags) {
        StringBuilder whereCondition = new StringBuilder();
        whereCondition.append(" WHERE ");
        for (int i = 0; i < tags.length; i++) {
            if (i != 0) {
                whereCondition.append(" AND ");
            }
			whereCondition.append("(" + KEY_CONTENT + " LIKE '%").append(tags[i]).append("%' OR ").append(KEY_TITLE)
					.append(" LIKE '%").append(tags[i]).append("%')");
        }
        // Trashed notes must be included in search results only if search if performed from trash
        whereCondition.append(" AND " + KEY_TRASHED + " IS ").append(Navigation.checkNavigation(Navigation.TRASH) ?
                "" : "" +
                " NOT ").append(" 1");

		return rx.Observable.from(getNotes(whereCondition.toString(), true))
				.map(note -> {
					boolean matches = rx.Observable.from(tags)
							.all(tag -> {
								Pattern p = Pattern.compile(".*(\\s|^)" + tag + "(\\s|$).*", Pattern.MULTILINE);
								return p.matcher((note.getTitle() + " " + note.getContent())).find();
							}).toBlocking().single();
					return matches ? note : null;
				})
				.filter(note -> note != null)
				.toList().toBlocking().single();
	}


    /**
     * Retrieves all attachments
     */
    public ArrayList<Attachment> getAllAttachments() {
        return getAttachments("");
    }


    /**
     * Retrieves attachments using a condition passed as parameter
     *
     * @return List of attachments
     */
    public ArrayList<Attachment> getAttachments(String whereCondition) {

        ArrayList<Attachment> attachmentsList = new ArrayList<>();
        String sql = "SELECT "
                + KEY_ATTACHMENT_ID + ","
                + KEY_ATTACHMENT_URI + ","
                + KEY_ATTACHMENT_NAME + ","
                + KEY_ATTACHMENT_SIZE + ","
                + KEY_ATTACHMENT_LENGTH + ","
                + KEY_ATTACHMENT_MIME_TYPE
                + " FROM " + TABLE_ATTACHMENTS
                + whereCondition;
        SQLiteDatabase db;
        Cursor cursor = null;

        try {

            cursor = getDatabase().rawQuery(sql, null);

            // Looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                Attachment mAttachment;
                do {
                    mAttachment = new Attachment(cursor.getLong(0),
                            Uri.parse(cursor.getString(1)), cursor.getString(2), cursor.getInt(3),
                            (long) cursor.getInt(4), cursor.getString(5));
                    attachmentsList.add(mAttachment);
                } while (cursor.moveToNext());
            }

        } finally {
            if (cursor != null)
                cursor.close();
        }
        return attachmentsList;
    }


    /**
     * Retrieves categories list from database
     *
     * @return List of categories
     */
    public ArrayList<Category> getCategories() {
        ArrayList<Category> categoriesList = new ArrayList<>();
        String sql = "SELECT "
                + KEY_CATEGORY_ID + ","
                + KEY_CATEGORY_NAME + ","
                + KEY_CATEGORY_DESCRIPTION + ","
                + KEY_CATEGORY_COLOR + ","
                + " COUNT(" + KEY_ID + ") count"
                + " FROM " + TABLE_CATEGORY
                + " LEFT JOIN ("
                + " SELECT " + KEY_ID + ", " + KEY_CATEGORY
                + " FROM " + TABLE_NOTES
                + " WHERE " + KEY_TRASHED + " IS NOT 1"
                + ") USING( " + KEY_CATEGORY + ") "
                + " GROUP BY "
                + KEY_CATEGORY_ID + ","
                + KEY_CATEGORY_NAME + ","
                + KEY_CATEGORY_DESCRIPTION + ","
                + KEY_CATEGORY_COLOR
                + " ORDER BY IFNULL(NULLIF(" + KEY_CATEGORY_NAME + ", ''),'zzzzzzzz') ";

        Cursor cursor = null;
        try {
            cursor = getDatabase().rawQuery(sql, null);
            // Looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    categoriesList.add(new Category(cursor.getLong(0),
                            cursor.getString(1), cursor.getString(2), cursor
                            .getString(3), cursor.getInt(4)));
                } while (cursor.moveToNext());
            }

        } finally {
            if (cursor != null)
                cursor.close();
        }
        return categoriesList;
    }


    /**
     * Updates or insert a new a category
     *
     * @param category Category to be updated or inserted
     * @return Rows affected or new inserted category id
     */
    public Category updateCategory(Category category) {
        ContentValues values = new ContentValues();
		values.put(KEY_CATEGORY_ID, category.getId() != null ? category.getId() : Calendar.getInstance()
				.getTimeInMillis());
		values.put(KEY_CATEGORY_NAME, category.getName());
        values.put(KEY_CATEGORY_DESCRIPTION, category.getDescription());
        values.put(KEY_CATEGORY_COLOR, category.getColor());
		getDatabase(true).insertWithOnConflict(TABLE_CATEGORY, KEY_CATEGORY_ID, values, SQLiteDatabase
				.CONFLICT_REPLACE);
		return category;
    }


    /**
     * Deletion of  a category
     *
     * @param category Category to be deleted
     * @return Number 1 if category's record has been deleted, 0 otherwise
     */
    public long deleteCategory(Category category) {
        long deleted;

        SQLiteDatabase db = getDatabase(true);
        // Un-categorize notes associated with this category
        ContentValues values = new ContentValues();
        values.put(KEY_CATEGORY, "");

        // Updating row
        db.update(TABLE_NOTES, values, KEY_CATEGORY + " = ?",
                new String[]{String.valueOf(category.getId())});

        // Delete category
        deleted = db.delete(TABLE_CATEGORY, KEY_CATEGORY_ID + " = ?",
                new String[]{String.valueOf(category.getId())});
        return deleted;
    }


    /**
     * Get note Category
     */
    public Category getCategory(Long id) {
        Category category = null;
        String sql = "SELECT "
                + KEY_CATEGORY_ID + ","
                + KEY_CATEGORY_NAME + ","
                + KEY_CATEGORY_DESCRIPTION + ","
                + KEY_CATEGORY_COLOR
                + " FROM " + TABLE_CATEGORY
                + " WHERE " + KEY_CATEGORY_ID + " = " + id;

        Cursor cursor = null;
        try {
            cursor = getDatabase().rawQuery(sql, null);

            // Looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                category = new Category(cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3));
            }

        } finally {
            if (cursor != null)
                cursor.close();
        }
        return category;
    }


    public int getCategorizedCount(Category category) {
        int count = 0;
        String sql = "SELECT COUNT(*)"
                + " FROM " + TABLE_NOTES
                + " WHERE " + KEY_CATEGORY + " = " + category.getId();

        Cursor cursor = null;
        try {
            cursor = getDatabase().rawQuery(sql, null);

            // Looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

        } finally {
            if (cursor != null)
                cursor.close();
        }
        return count;
    }


    /**
     * Retrieves statistics data based on app usage
     */
    public Stats getStats() {
        Stats mStats = new Stats();

        // Categories
        mStats.setCategories(getCategories().size());

        // Everything about notes and their text stats
        int notesActive = 0, notesArchived = 0, notesTrashed = 0, reminders = 0, remindersFuture = 0, checklists = 0,
                notesMasked = 0, tags = 0, locations = 0;
        int totalWords = 0, totalChars = 0, maxWords = 0, maxChars = 0, avgWords = 0, avgChars = 0;
        int words, chars;
        List<Note> notes = getAllNotes(false);
        for (Note note : notes) {
            if (note.isTrashed()) {
                notesTrashed++;
            } else if (note.isArchived()) {
                notesArchived++;
            } else {
                notesActive++;
            }
            if (note.getAlarm() != null && Long.parseLong(note.getAlarm()) > 0) {
                if (Long.parseLong(note.getAlarm()) > Calendar.getInstance().getTimeInMillis()) {
                    remindersFuture++;
                } else {
                    reminders++;
                }
            }
            if (note.isChecklist()) {
                checklists++;
            }
            if (note.isLocked()) {
                notesMasked++;
            }
            tags += TagsHelper.retrieveTags(note).size();
            if (note.getLongitude() != null && note.getLongitude() != 0) {
                locations++;
            }
            words = getWords(note);
            chars = getChars(note);
            if (words > maxWords) {
                maxWords = words;
            }
            if (chars > maxChars) {
                maxChars = chars;
            }
            totalWords += words;
            totalChars += chars;
        }
        mStats.setNotesActive(notesActive);
        mStats.setNotesArchived(notesArchived);
        mStats.setNotesTrashed(notesTrashed);
        mStats.setReminders(reminders);
        mStats.setRemindersFutures(remindersFuture);
        mStats.setNotesChecklist(checklists);
        mStats.setNotesMasked(notesMasked);
        mStats.setTags(tags);
        mStats.setLocation(locations);
        avgWords = totalWords / (notes.size() != 0 ? notes.size() : 1);
		avgChars = totalChars / (notes.size() != 0 ? notes.size() : 1);

        mStats.setWords(totalWords);
        mStats.setWordsMax(maxWords);
        mStats.setWordsAvg(avgWords);
        mStats.setChars(totalChars);
        mStats.setCharsMax(maxChars);
        mStats.setCharsAvg(avgChars);

        // Everything about attachments
        int attachmentsAll = 0, images = 0, videos = 0, audioRecordings = 0, sketches = 0, files = 0;
        List<Attachment> attachments = getAllAttachments();
        for (Attachment attachment : attachments) {
            if (Constants.MIME_TYPE_IMAGE.equals(attachment.getMime_type())) {
                images++;
            } else if (Constants.MIME_TYPE_VIDEO.equals(attachment.getMime_type())) {
                videos++;
            } else if (Constants.MIME_TYPE_AUDIO.equals(attachment.getMime_type())) {
                audioRecordings++;
            } else if (Constants.MIME_TYPE_SKETCH.equals(attachment.getMime_type())) {
                sketches++;
            } else if (Constants.MIME_TYPE_FILES.equals(attachment.getMime_type())) {
                files++;
            }
        }
        mStats.setAttachments(attachmentsAll);
        mStats.setImages(images);
        mStats.setVideos(videos);
        mStats.setAudioRecordings(audioRecordings);
        mStats.setSketches(sketches);
        mStats.setFiles(files);

        return mStats;
    }


    public void setReminderFired(long noteId, boolean fired) {
        ContentValues values = new ContentValues();
        values.put(KEY_REMINDER_FIRED, fired);
        getDatabase(true).update(TABLE_NOTES, values, KEY_ID + " = ?", new String[]{String.valueOf(noteId)});
    }


}
