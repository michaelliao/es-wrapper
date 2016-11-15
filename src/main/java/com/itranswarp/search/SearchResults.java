package com.itranswarp.search;

import java.util.List;

public class SearchResults<T> {

	public final long hits;

	public final List<T> results;

	public SearchResults(long hits, List<T> results) {
		this.hits = hits;
		this.results = results;
	}
}
