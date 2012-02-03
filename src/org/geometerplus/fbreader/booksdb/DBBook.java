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

import org.geometerplus.zlibrary.core.util.ZLMiscUtil;
import org.geometerplus.zlibrary.core.filesystem.*;

import org.geometerplus.fbreader.library.*;
import org.geometerplus.fbreader.formats.*;
import org.geometerplus.fbreader.Paths;

public class DBBook extends Book {
	public static Book getById(long bookId) {
		final DBBook book = BooksDatabase.Instance().loadBook(bookId);
		if (book == null) {
			return null;
		}
		book.loadLists();

		final ZLFile bookFile = book.File;
		final ZLPhysicalFile physicalFile = bookFile.getPhysicalFile();
		if (physicalFile == null) {
			return book;
		}
		if (!physicalFile.exists()) {
			return null;
		}

		FileInfoSet fileInfos = new FileInfoSet(physicalFile);
		if (fileInfos.check(physicalFile, physicalFile != bookFile)) {
			return book;
		}
		fileInfos.save();

		return book.readMetaInfo() ? book : null;
	}

	public static Book getByFile(ZLFile bookFile) {
		if (bookFile == null) {
			return null;
		}

		final ZLPhysicalFile physicalFile = bookFile.getPhysicalFile();
		if (physicalFile != null && !physicalFile.exists()) {
			return null;
		}

		final FileInfoSet fileInfos = new FileInfoSet(bookFile);

		DBBook book = BooksDatabase.Instance().loadBookByFile(fileInfos.getId(bookFile), bookFile);
		if (book != null) {
			book.loadLists();
		}

		if (book != null && fileInfos.check(physicalFile, physicalFile != bookFile)) {
			return book;
		}
		fileInfos.save();

		if (book == null) {
			book = new DBBook(bookFile);
		}
		if (book.readMetaInfo()) {
			book.save();
			return book;
		}
		return null;
	}

	private long myId;

	private String myEncoding;
	private String myLanguage;
	private String myTitle;
	private List<Author> myAuthors;
	private List<Tag> myTags;
	private SeriesInfo mySeriesInfo;

	private boolean myIsSaved;

	DBBook(long id, ZLFile file, String title, String encoding, String language) {
		super(file);
		myId = id;
		myTitle = title;
		myEncoding = encoding;
		myLanguage = language;
		myIsSaved = true;
	}

	DBBook(ZLFile file) {
		super(file);
		myId = -1;
	}

	@Override
	public long getId() {
		return myId;
	}

	private void loadLists() {
		final BooksDatabase database = BooksDatabase.Instance();
		myAuthors = database.loadAuthors(myId);
		myTags = database.loadTags(myId);
		mySeriesInfo = database.loadSeriesInfo(myId);
		myIsSaved = true;
	}

	@Override
	public void reloadInfoFromDatabase() {
		final BooksDatabase database = BooksDatabase.Instance();
		database.reloadBook(this);
		myAuthors = database.loadAuthors(myId);
		myTags = database.loadTags(myId);
		mySeriesInfo = database.loadSeriesInfo(myId);
		myIsSaved = true;
	}

	@Override
	protected boolean readMetaInfo() {
		myEncoding = null;
		myLanguage = null;
		myTitle = null;
		myAuthors = null;
		myTags = null;
		mySeriesInfo = null;

		myIsSaved = false;

		final FormatPlugin plugin = PluginCollection.Instance().getPlugin(File);
		if (plugin == null || !plugin.readMetaInfo(this)) {
			return false;
		}
		if (myTitle == null || myTitle.length() == 0) {
			final String fileName = File.getShortName();
			final int index = fileName.lastIndexOf('.');
			setTitle(index > 0 ? fileName.substring(0, index) : fileName);
		}
		final String demoPathPrefix = Paths.BooksDirectoryOption().getValue() + java.io.File.separator + "Demos" + java.io.File.separator;
		if (File.getPath().startsWith(demoPathPrefix)) {
			final String demoTag = LibraryUtil.resource().getResource("demo").getValue();
			setTitle(getTitle() + " (" + demoTag + ")");
			addTag(demoTag);
		}
		return true;
	}

	@Override
	public String getTitle() {
		return myTitle;
	}

	@Override
	public void setTitle(String title) {
		if (!ZLMiscUtil.equals(myTitle, title)) {
			myTitle = title;
			myIsSaved = false;
		}
	}

	@Override
	public List<Author> authors() {
		return (myAuthors != null) ? Collections.unmodifiableList(myAuthors) : Collections.<Author>emptyList();
	}

	@Override
	protected void addAuthor(Author author) {
		if (author == null) {
			return;
		}
		if (myAuthors == null) {
			myAuthors = new ArrayList<Author>();
			myAuthors.add(author);
			myIsSaved = false;
		} else if (!myAuthors.contains(author)) {
			myAuthors.add(author);
			myIsSaved = false;
		}
	}

	void addAuthorWithNoCheck(Author author) {
		if (myAuthors == null) {
			myAuthors = new ArrayList<Author>();
		}
		myAuthors.add(author);
	}

	@Override
	public SeriesInfo getSeriesInfo() {
		return mySeriesInfo;
	}

	@Override
	public void setSeriesInfo(String name, float index) {
		if (mySeriesInfo == null) {
			if (name != null) {
				mySeriesInfo = new SeriesInfo(name, index);
				myIsSaved = false;
			}
		} else if (name == null) {
			mySeriesInfo = null;
			myIsSaved = false;
		} else if (!name.equals(mySeriesInfo.Name) || mySeriesInfo.Index != index) {
			mySeriesInfo = new SeriesInfo(name, index);
			myIsSaved = false;
		}
	}

	void setSeriesInfoWithNoCheck(String name, float index) {
		mySeriesInfo = new SeriesInfo(name, index);
	}

	@Override
	public List<Tag> tags() {
		return (myTags != null) ? Collections.unmodifiableList(myTags) : Collections.<Tag>emptyList();
	}

	@Override
	public void addTag(Tag tag) {
		if (tag != null) {
			if (myTags == null) {
				myTags = new ArrayList<Tag>();
			}
			if (!myTags.contains(tag)) {
				myTags.add(tag);
				myIsSaved = false;
			}
		}
	}

	void addTagWithNoCheck(Tag tag) {
		if (myTags == null) {
			myTags = new ArrayList<Tag>();
		}
		myTags.add(tag);
	}

	@Override
	public boolean save() {
		if (myIsSaved) {
			return false;
		}
		final BooksDatabase database = BooksDatabase.Instance();
		database.executeAsATransaction(new Runnable() {
			public void run() {
				if (myId >= 0) {
					final FileInfoSet fileInfos = new FileInfoSet(File);
					database.updateBookInfo(myId, fileInfos.getId(File), myEncoding, myLanguage, myTitle);
				} else {
					myId = database.insertBookInfo(File, myEncoding, myLanguage, myTitle);
					storeAllVisitedHyperinks();
				}

				long index = 0;
				database.deleteAllBookAuthors(myId);
				for (Author author : authors()) {
					database.saveBookAuthorInfo(myId, index++, author);
				}
				database.deleteAllBookTags(myId);
				for (Tag tag : tags()) {
					database.saveBookTagInfo(myId, tag);
				}
				database.saveBookSeriesInfo(myId, mySeriesInfo);
			}
		});

		myIsSaved = true;
		return true;
	}

	private Set<String> myVisitedHyperlinks;
	private void initHyperlinkSet() {
		if (myVisitedHyperlinks == null) {
			myVisitedHyperlinks = new TreeSet<String>();
			if (myId != -1) {
				myVisitedHyperlinks.addAll(BooksDatabase.Instance().loadVisitedHyperlinks(myId));
			}
		}
	}

	@Override
	public boolean isHyperlinkVisited(String linkId) {
		initHyperlinkSet();
		return myVisitedHyperlinks.contains(linkId);
	}

	@Override
	public void markHyperlinkAsVisited(String linkId) {
		initHyperlinkSet();
		if (!myVisitedHyperlinks.contains(linkId)) {
			myVisitedHyperlinks.add(linkId);
			if (myId != -1) {
				BooksDatabase.Instance().addVisitedHyperlink(myId, linkId);
			}
		}
	}

	private void storeAllVisitedHyperinks() {
		if (myId != -1 && myVisitedHyperlinks != null) {
			for (String linkId : myVisitedHyperlinks) {
				BooksDatabase.Instance().addVisitedHyperlink(myId, linkId);
			}
		}
	}
}
