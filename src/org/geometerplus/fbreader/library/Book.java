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

import java.lang.ref.WeakReference;
import java.util.*;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.geometerplus.zlibrary.core.util.ZLMiscUtil;
import org.geometerplus.zlibrary.core.filesystem.*;
import org.geometerplus.zlibrary.core.image.ZLImage;

import org.geometerplus.zlibrary.text.view.ZLTextPosition;

import org.geometerplus.fbreader.formats.*;

import org.geometerplus.fbreader.Paths;

public abstract class Book {
	public final ZLFile File;

	private static final WeakReference<ZLImage> NULL_IMAGE = new WeakReference<ZLImage>(null);
	private WeakReference<ZLImage> myCover;

	protected Book(ZLFile file) {
		File = file;
		readMetaInfo();
	}

	public void reloadInfoFromFile() {
		try {
			readMetaInfo();
			save();
		} catch (BookReadingException e) {
			// ignore
		}
	}

	protected abstract boolean readMetaInfo();

	public abstract long getId();

	public abstract String getTitle();
	public abstract void setTitle(String title);

	public abstract List<Author> authors();
	protected abstract void addAuthor(Author author);

	public void addAuthor(String name) {
		addAuthor(name, "");
	}

	public void addAuthor(String name, String sortKey) {
		String strippedName = name;
		strippedName.trim();
		if (strippedName.length() == 0) {
			return;
		}

		String strippedKey = sortKey;
		strippedKey.trim();
		if (strippedKey.length() == 0) {
			int index = strippedName.lastIndexOf(' ');
			if (index == -1) {
				strippedKey = strippedName;
			} else {
				strippedKey = strippedName.substring(index + 1);
				while ((index >= 0) && (strippedName.charAt(index) == ' ')) {
					--index;
				}
				strippedName = strippedName.substring(0, index + 1) + ' ' + strippedKey;
			}
		}

		addAuthor(new Author(strippedName, strippedKey));
	}

	public abstract SeriesInfo getSeriesInfo();
	public abstract void setSeriesInfo(String name, float index);

	public abstract String getLanguage();
	public abstract void setLanguage(String language);
	public abstract String getEncoding();
	public abstract String getEncodingNoDetection();
	public abstract void setEncoding(String encoding);

	public abstract List<Tag> tags();
	public abstract void addTag(Tag tag);

	public void addTag(String tagName) {
		addTag(Tag.getTag(null, tagName));
	}

	boolean matches(String pattern) {
		final String title = getTitle();
		if (title != null && ZLMiscUtil.matchesIgnoreCase(title, pattern)) {
			return true;
		}
		final SeriesInfo series = getSeriesInfo();
		if (series != null && ZLMiscUtil.matchesIgnoreCase(series.Name, pattern)) {
			return true;
		}
		for (Author a : authors()) {
			if (ZLMiscUtil.matchesIgnoreCase(a.DisplayName, pattern)) {
				return true;
			}
		}
		for (Tag t : tags()) {
			if (ZLMiscUtil.matchesIgnoreCase(t.Name, pattern)) {
				return true;
			}
		}
		if (ZLMiscUtil.matchesIgnoreCase(File.getLongName(), pattern)) {
			return true;
		}
		return false;
	}

	public abstract boolean save();

	public abstract boolean isHyperlinkVisited(String linkId);
	public abstract void markHyperlinkAsVisited(String linkId);

	public abstract void insertIntoBookList();

	public String getContentHashCode() {
		InputStream stream = null;

		try {
			final MessageDigest hash = MessageDigest.getInstance("SHA-256");
			stream = File.getInputStream();

			final byte[] buffer = new byte[2048];
			while (true) {
				final int nread = stream.read(buffer);
				if (nread == -1) {
					break;
				}
				hash.update(buffer, 0, nread);
			}

			final Formatter f = new Formatter();
			for (byte b : hash.digest()) {
				f.format("%02X", b & 0xFF);
			}
			return f.toString();
		} catch (IOException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
				}
			}
		}
	}

	synchronized ZLImage getCover() {
		if (myCover == NULL_IMAGE) {
			return null;
		} else if (myCover != null) {
			final ZLImage image = myCover.get();
			if (image != null) {
				return image;
			}
		}
		ZLImage image = null;
		final FormatPlugin plugin = PluginCollection.Instance().getPlugin(File);
		if (plugin != null) {
			image = plugin.readCover(File);
		}
		myCover = image != null ? new WeakReference<ZLImage>(image) : NULL_IMAGE;
		return image;
	}

	@Override
	public int hashCode() {
		return File.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Book)) {
			return false;
		}
		return File.equals(((Book)o).File);
	}
}
