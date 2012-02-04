/*
 * Copyright (C) 2007-2012 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.booksdb;

import java.util.*;
import java.io.File;

import org.geometerplus.zlibrary.core.filesystem.*;

import org.geometerplus.fbreader.library.*;
import org.geometerplus.fbreader.Paths;

public class DBLibrary extends Library {
	private static DBLibrary ourInstance;
	public static Library Instance() {
		if (ourInstance == null) {
			ourInstance = new DBLibrary(BooksDatabase.Instance());
		}
		return ourInstance;
	}

	private final BooksDatabase myDatabase;

	private DBLibrary(BooksDatabase db) {
		myDatabase = db;
		startBuild();
	}

	@Override
	public Book getRecentBook() {
		List<Long> recentIds = myDatabase.loadRecentBookIds();
		return recentIds.size() > 0 ? getBookById(recentIds.get(0)) : null;
	}

	@Override
	public Book getPreviousBook() {
		List<Long> recentIds = myDatabase.loadRecentBookIds();
		return recentIds.size() > 1 ? getBookById(recentIds.get(1)) : null;
	}

	@Override
	public boolean removeBook(Book book, int removeMode) {
		if (super.removeBook(book, removeMode)) {
			final List<Long> ids = myDatabase.loadRecentBookIds();
			if (ids.remove(book.getId())) {
				myDatabase.saveRecentBookIds(ids);
			}
			myDatabase.removeFromFavorites(book.getId());
			myDatabase.deleteFromBookList(book.getId());

			return true;
		}
		return false;
	}

	@Override
	public void addBookToRecentList(Book book) {
		final List<Long> ids = myDatabase.loadRecentBookIds();
		final Long bookId = book.getId();
		ids.remove(bookId);
		ids.add(0, bookId);
		if (ids.size() > 12) {
			ids.remove(12);
		}
		myDatabase.saveRecentBookIds(ids);
	}

	@Override
	public boolean addBookToFavorites(Book book) {
		if (super.addBookToFavorites(book)) {
			myDatabase.addToFavorites(book.getId());
			return true;
		}
		return false;
	}

	@Override
	public boolean removeBookFromFavorites(Book book) {
		if (super.removeBookFromFavorites(book)) {
			myDatabase.removeFromFavorites(book.getId());
			return true;
		}
		return false;
	}

	@Override
	public List<Bookmark> allBookmarks() {
		return BooksDatabase.Instance().loadAllVisibleBookmarks();
	}

	@Override
	public List<Bookmark> invisibleBookmarks(Book book) {
		final List<Bookmark> list = BooksDatabase.Instance().loadBookmarks(book.getId(), false);
		Collections.sort(list, new Bookmark.ByTimeComparator());
		return list;
	}

	@Override
	public DBBook getBookByFile(ZLFile file) {
		return DBBook.getByFile(myDatabase, file);
	}

	@Override
	public DBBook getBookById(long id) {
		return DBBook.getById(myDatabase, id);
	}

	private void build() {
		// Step 0: get database books marked as "existing"
		final FileInfoSet fileInfos = new FileInfoSet();
		final Map<Long,DBBook> savedBooksByFileId = myDatabase.loadBooks(fileInfos, true);
		final Map<Long,DBBook> savedBooksByBookId = new HashMap<Long,DBBook>();
		for (DBBook b : savedBooksByFileId.values()) {
			savedBooksByBookId.put(b.getId(), b);
		}

		// Step 1: set tree parameters,
        // add "existing" books into recent and favorites lists
		_treeSetParameters(savedBooksByFileId.values());

		final List<Book> recentList = new LinkedList<Book>();
		for (long id : myDatabase.loadRecentBookIds()) {
			DBBook book = savedBooksByBookId.get(id);
			if (book == null) {
				book = getBookById(id);
				if (book != null && !book.File.exists()) {
					book = null;
				}
			}
			if (book != null) {
				recentList.add(book);
			}
		}
		_treeSetRecentList(recentList);

		final List<Book> favoritesList = new LinkedList<Book>();
		for (long id : myDatabase.loadFavoritesIds()) {
			DBBook book = savedBooksByBookId.get(id);
			if (book == null) {
				book = getBookById(id);
				if (book != null && !book.File.exists()) {
					book = null;
				}
			}
			if (book != null) {
				favoritesList.add(book);
			}
		}
		_treeSetFavoritesList(favoritesList);

		fireModelChangedEvent(ChangeListener.Code.BookAdded);

		// Step 2: check if files corresponding to "existing" books really exists;
		//         add books to library if yes (and reload book info if needed);
		//         remove from recent/favorites list if no;
		//         collect newly "orphaned" books
		final Set<DBBook> orphanedBooks = new HashSet<DBBook>();
		int count = 0;
		for (DBBook book : savedBooksByFileId.values()) {
			synchronized (this) {
				if (book.File.exists()) {
					boolean doAdd = true;
					final ZLPhysicalFile file = book.File.getPhysicalFile();
					if (file == null) {
						continue;
					}
					if (!fileInfos.check(file, true)) {
						if (book.readMetaInfo()) {
							book.save();
						} else {
							doAdd = false;
						}
						file.setCached(false);
					}
					if (doAdd) {
						addBookToLibrary(book);
						if (++count % 16 == 0) {
							fireModelChangedEvent(ChangeListener.Code.BookAdded);
						}
					}
				} else {
					_treeRemoveOrphanedBook(book);
					orphanedBooks.add(book);
				}
			}
		}
		fireModelChangedEvent(ChangeListener.Code.BookAdded);
		myDatabase.setExistingFlag(orphanedBooks, false);

		// Step 3: collect books from physical files; add new, update already added,
		//         unmark orphaned as existing again, collect newly added
		final Map<Long,DBBook> orphanedBooksByFileId = myDatabase.loadBooks(fileInfos, false);
		final Set<DBBook> newBooks = new HashSet<DBBook>();

		final List<ZLPhysicalFile> physicalFilesList = collectPhysicalFiles();
		for (ZLPhysicalFile file : physicalFilesList) {
			collectBooks(
				file, fileInfos,
				savedBooksByFileId, orphanedBooksByFileId,
				newBooks,
				!fileInfos.check(file, true)
			);
			file.setCached(false);
		}
		
		// Step 4: add help file
		final ZLFile helpFile = getHelpFile();
		DBBook helpBook = savedBooksByFileId.get(fileInfos.getId(helpFile));
		if (helpBook == null) {
			helpBook = new DBBook(helpFile);
			helpBook.readMetaInfo();
		}
		addBookToLibrary(helpBook);
		fireModelChangedEvent(ChangeListener.Code.BookAdded);

		// Step 5: save changes into database
		fileInfos.save();

		myDatabase.executeAsATransaction(new Runnable() {
			public void run() {
				for (DBBook book : newBooks) {
					book.save();
				}
			}
		});
		myDatabase.setExistingFlag(newBooks, true);
	}

	private List<ZLPhysicalFile> collectPhysicalFiles() {
		final Queue<ZLFile> dirQueue = new LinkedList<ZLFile>();
		final HashSet<ZLFile> dirSet = new HashSet<ZLFile>();
		final LinkedList<ZLPhysicalFile> fileList = new LinkedList<ZLPhysicalFile>();

		dirQueue.offer(new ZLPhysicalFile(new File(Paths.BooksDirectoryOption().getValue())));

		while (!dirQueue.isEmpty()) {
			for (ZLFile file : dirQueue.poll().children()) {
				if (file.isDirectory()) {
					if (!dirSet.contains(file)) {
						dirQueue.add(file);
						dirSet.add(file);
					}
				} else {
					file.setCached(true);
					fileList.add((ZLPhysicalFile)file);
				}
			}
		}
		return fileList;
	}

	private void collectBooks(
		ZLFile file, FileInfoSet fileInfos,
		Map<Long,DBBook> savedBooksByFileId, Map<Long,DBBook> orphanedBooksByFileId,
		Set<DBBook> newBooks,
		boolean doReadMetaInfo
	) {
		final long fileId = fileInfos.getId(file);
		if (savedBooksByFileId.get(fileId) != null) {
			return;
		}

		DBBook book = orphanedBooksByFileId.get(fileId);
		if (book != null && (!doReadMetaInfo || book.readMetaInfo())) {
			addBookToLibrary(book);
			fireModelChangedEvent(ChangeListener.Code.BookAdded);
			newBooks.add(book);
			return;
		}

		book = new DBBook(file);
		if (book.readMetaInfo()) {
			addBookToLibrary(book);
			fireModelChangedEvent(ChangeListener.Code.BookAdded);
			newBooks.add(book);
			return;
		}

		if (file.isArchive()) {
			for (ZLFile entry : fileInfos.archiveEntries(file)) {
				collectBooks(
					entry, fileInfos,
					savedBooksByFileId, orphanedBooksByFileId,
					newBooks,
					doReadMetaInfo
				);
			}
		}
	}

	private volatile boolean myBuildStarted = false;

	private synchronized void startBuild() {
		if (myBuildStarted) {
			fireModelChangedEvent(ChangeListener.Code.StatusChanged);
			return;
		}
		myBuildStarted = true;

		addStatusFlags(STATUS_LOADING);
		final Thread builder = new Thread("Library.build") {
			public void run() {
				try {
					build();
				} finally {
					removeStatusFlags(STATUS_LOADING);
				}
			}
		};
		builder.setPriority((Thread.MIN_PRIORITY + Thread.NORM_PRIORITY) / 2);
		builder.start();
	}
}
