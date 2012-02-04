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

package org.geometerplus.fbreader.library;

import java.io.File;
import java.util.*;

import org.geometerplus.zlibrary.core.filesystem.*;

import org.geometerplus.fbreader.tree.FBTree;
import org.geometerplus.fbreader.booksdb.*;

public abstract class Library extends AbstractLibrary {
	public static final String ROOT_FOUND = "found";
	public static final String ROOT_FAVORITES = "favorites";
	public static final String ROOT_RECENT = "recent";
	public static final String ROOT_BY_AUTHOR = "byAuthor";
	public static final String ROOT_BY_TITLE = "byTitle";
	public static final String ROOT_BY_SERIES = "bySeries";
	public static final String ROOT_BY_TAG = "byTag";
	public static final String ROOT_FILE_TREE = "fileTree";

	private final List<Book> myBooks = Collections.synchronizedList(new LinkedList<Book>());
	private final RootTree myRootTree = new RootTree();
	private boolean myDoGroupTitlesByFirstLetter;

	protected final static int STATUS_LOADING = 1;
	protected final static int STATUS_SEARCHING = 2;
	private volatile int myStatusMask = 0;

	protected synchronized void addStatusFlags(int flags) {
		if ((myStatusMask & flags) != flags) {
			myStatusMask = myStatusMask | flags;
			fireModelChangedEvent(ChangeListener.Code.StatusChanged);
		}
	}

	protected synchronized void removeStatusFlags(int flags) {
		if ((myStatusMask & flags) != 0) {
			myStatusMask = myStatusMask & ~flags;
			fireModelChangedEvent(ChangeListener.Code.StatusChanged);
		}
	}

	protected Library() {
		new FavoritesTree(myRootTree, ROOT_FAVORITES);
		new FirstLevelTree(myRootTree, ROOT_RECENT);
		new FirstLevelTree(myRootTree, ROOT_BY_AUTHOR);
		new FirstLevelTree(myRootTree, ROOT_BY_TITLE);
		new FirstLevelTree(myRootTree, ROOT_BY_TAG);
		new FileFirstLevelTree(this, myRootTree, ROOT_FILE_TREE);
	}

	public LibraryTree getRootTree() {
		return myRootTree;
	}

	private FirstLevelTree getFirstLevelTree(String key) {
		return (FirstLevelTree)myRootTree.getSubTree(key);
	}

	public LibraryTree getLibraryTree(LibraryTree.Key key) {
		if (key == null) {
			return null;
		}
		if (key.Parent == null) {
			return key.Id.equals(myRootTree.getUniqueKey().Id) ? myRootTree : null;
		}
		final LibraryTree parentTree = getLibraryTree(key.Parent);
		return parentTree != null ? (LibraryTree)parentTree.getSubTree(key.Id) : null;
	}

	private final List<?> myNullList = Collections.singletonList(null);

	private LibraryTree getTagTree(Tag tag) {
		if (tag == null || tag.Parent == null) {
			return getFirstLevelTree(ROOT_BY_TAG).getTagSubTree(tag);
		} else {
			return getTagTree(tag.Parent).getTagSubTree(tag);
		}
	}

	protected synchronized void addBookToLibrary(Book book) {
		myBooks.add(book);

		List<Author> authors = book.authors();
		if (authors.isEmpty()) {
			authors = (List<Author>)myNullList;
		}
		final SeriesInfo seriesInfo = book.getSeriesInfo();
		for (Author a : authors) {
			final AuthorTree authorTree = getFirstLevelTree(ROOT_BY_AUTHOR).getAuthorSubTree(a);
			if (seriesInfo == null) {
				authorTree.getBookSubTree(book, false);
			} else {
				authorTree.getSeriesSubTree(seriesInfo.Name).getBookInSeriesSubTree(book);
			}
		}

		if (seriesInfo != null) {
			FirstLevelTree seriesRoot = getFirstLevelTree(ROOT_BY_SERIES);
			if (seriesRoot == null) {
				seriesRoot = new FirstLevelTree(
					myRootTree,
					myRootTree.indexOf(getFirstLevelTree(ROOT_BY_TITLE)) + 1,
					ROOT_BY_SERIES
				);
			}
			seriesRoot.getSeriesSubTree(seriesInfo.Name).getBookInSeriesSubTree(book);
		}

		if (myDoGroupTitlesByFirstLetter) {
			final String letter = TitleTree.firstTitleLetter(book);
			if (letter != null) {
				final TitleTree tree =
					getFirstLevelTree(ROOT_BY_TITLE).getTitleSubTree(letter);
				tree.getBookSubTree(book, true);
			}
		} else {
			getFirstLevelTree(ROOT_BY_TITLE).getBookSubTree(book, true);
		}

		List<Tag> tags = book.tags();
		if (tags.isEmpty()) {
			tags = (List<Tag>)myNullList;
		}
		for (Tag t : tags) {
			getTagTree(t).getBookSubTree(book, true);
		}

		final SearchResultsTree found =
			(SearchResultsTree)getFirstLevelTree(ROOT_FOUND);
		if (found != null && book.matches(found.getPattern())) {
			found.getBookSubTree(book, true);
		}
	}

	private void removeFromTree(String rootId, Book book) {
		final FirstLevelTree tree = getFirstLevelTree(rootId);
		if (tree != null) {
			tree.removeBook(book, false);
		}
	}

	private void refreshInTree(String rootId, Book book) {
		final FirstLevelTree tree = getFirstLevelTree(rootId);
		if (tree != null) {
			int index = tree.indexOf(new BookTree(book, true));
			if (index >= 0) {
				tree.removeBook(book, false);
				new BookTree(tree, book, true, index);
			}
		}
	}

	public synchronized void refreshBookInfo(Book book) {
		if (book == null) {
			return;
		}

		myBooks.remove(book);
		refreshInTree(ROOT_FAVORITES, book);
		refreshInTree(ROOT_RECENT, book);
		removeFromTree(ROOT_FOUND, book);
		removeFromTree(ROOT_BY_TITLE, book);
		removeFromTree(ROOT_BY_SERIES, book);
		removeFromTree(ROOT_BY_AUTHOR, book);
		removeFromTree(ROOT_BY_TAG, book);
		addBookToLibrary(book);
		fireModelChangedEvent(ChangeListener.Code.BookAdded);
	}

	@Override
	public boolean isUpToDate() {
		return myStatusMask == 0;
	}

	@Override
	public void startBookSearch(final String pattern) {
		addStatusFlags(STATUS_SEARCHING);
		final Thread searcher = new Thread("Library.searchBooks") {
			public void run() {
				try {
					searchBooks(pattern);
				} finally {
					removeStatusFlags(STATUS_SEARCHING);
				}
			}
		};
		searcher.setPriority((Thread.MIN_PRIORITY + Thread.NORM_PRIORITY) / 2);
		searcher.start();
	}

	private void searchBooks(String pattern) {
		if (pattern == null) {
			fireModelChangedEvent(ChangeListener.Code.NotFound);
			return;
		}

		pattern = pattern.toLowerCase();

		final SearchResultsTree oldSearchResults = (SearchResultsTree)getFirstLevelTree(ROOT_FOUND);
		if (oldSearchResults != null && pattern.equals(oldSearchResults.getPattern())) {
			fireModelChangedEvent(ChangeListener.Code.Found);
			return;
		}
		
		FirstLevelTree newSearchResults = null;
		final List<Book> booksCopy;
		synchronized (myBooks) {
			booksCopy = new ArrayList<Book>(myBooks);
		}
		for (Book book : booksCopy) {
			if (book.matches(pattern)) {
				synchronized (this) {
					if (newSearchResults == null) {
						if (oldSearchResults != null) {
							oldSearchResults.removeSelf();
						}
						newSearchResults = new SearchResultsTree(myRootTree, ROOT_FOUND, pattern);
						fireModelChangedEvent(ChangeListener.Code.Found);
					}
					newSearchResults.getBookSubTree(book, true);
					fireModelChangedEvent(ChangeListener.Code.BookAdded);
				}
			}
		}
		if (newSearchResults == null) {
			fireModelChangedEvent(ChangeListener.Code.NotFound);
		}
	}

	@Override
	public boolean isBookInFavorites(Book book) {
		if (book == null) {
			return false;
		}
		final LibraryTree rootFavorites = getFirstLevelTree(ROOT_FAVORITES);
		for (FBTree tree : rootFavorites.subTrees()) {
			if (tree instanceof BookTree && book.equals(((BookTree)tree).Book)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean addBookToFavorites(Book book) {
		if (isBookInFavorites(book)) {
			return false;
		}
		getFirstLevelTree(ROOT_FAVORITES).getBookSubTree(book, true);
		return true;
	}

	@Override
	public boolean removeBookFromFavorites(Book book) {
		if (getFirstLevelTree(ROOT_FAVORITES).removeBook(book, false)) {
			fireModelChangedEvent(ChangeListener.Code.BookRemoved);
			return true;
		}
		return false;
	}

	@Override
	public boolean canRemoveBookFile(Book book) {
		ZLFile file = book.File;
		if (file.getPhysicalFile() == null) {
			return false;
		}
		while (file instanceof ZLArchiveEntryFile) {
			file = file.getParent();
			if (file.children().size() != 1) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean removeBook(Book book, int removeMode) {
		if (removeMode == REMOVE_DONT_REMOVE) {
			return false;
		}
		myBooks.remove(book);
		getFirstLevelTree(ROOT_RECENT).removeBook(book, false);
		getFirstLevelTree(ROOT_FAVORITES).removeBook(book, false);
		myRootTree.removeBook(book, true);

		if ((removeMode & REMOVE_FROM_DISK) != 0) {
			book.File.getPhysicalFile().delete();
		}

		return true;
	}

	protected void _treeSetRecentList(Collection<Book> books) {
		for (Book b : books) {
			new BookTree(getFirstLevelTree(ROOT_RECENT), b, true);
		}
	}

	protected void _treeSetFavoritesList(Collection<Book> books) {
		for (Book b : books) {
			getFirstLevelTree(ROOT_FAVORITES).getBookSubTree(b, true);
		}
	}

	protected void _treeRemoveOrphanedBook(Book book) {
		myRootTree.removeBook(book, true);
		fireModelChangedEvent(ChangeListener.Code.BookRemoved);
	}

	protected void _treeSetParameters(Collection<? extends Book> books) {
		if (books.size() > 10) {
			final HashSet<String> letterSet = new HashSet<String>();
			for (Book b : books) {
				final String letter = TitleTree.firstTitleLetter(b);
				if (letter != null) {
					letterSet.add(letter);
				}
			}
			myDoGroupTitlesByFirstLetter = books.size() > letterSet.size() * 5 / 4;
		}
	}
}
