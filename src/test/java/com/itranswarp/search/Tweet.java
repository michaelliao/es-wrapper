package com.itranswarp.search;

@SearchableDocument
public class Tweet {

	public static final int STYLE_A = 1;
	public static final int STYLE_B = 2;

	public Tweet() {
	}

	public Tweet(String id, String name, int style, boolean gender, String via, String content, long createdAt) {
		this.id = id;
		this.name = name;
		this.style = style;
		this.gender = gender;
		this.via = via;
		this.content = content;
		this.createdAt = createdAt;
	}

	@SearchableId
	public String id;

	@SearchableField
	public String name;

	@SearchableField
	public int style;

	@SearchableField
	public boolean gender;

	@SearchableField(keyword = true)
	public String via;

	@SearchableField
	public String content;

	@SearchableField
	public long createdAt;

	public String toJson() {
		return JsonUtil.toJson(this);
	}

	@Override
	public String toString() {
		return "----- Tweet @ " + this.id + " -----\n" + "     name: " + this.name + "\n" + "    style: " + this.style
				+ "\n" + "   gender: " + this.gender + "\n" + "      via: " + this.via + "\n" + "  content: "
				+ this.content + "\n" + "createdAt: " + this.createdAt + "\n--------------------";
	}
}
