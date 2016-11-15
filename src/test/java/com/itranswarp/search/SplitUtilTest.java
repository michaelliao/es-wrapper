package com.itranswarp.search;

import static org.junit.Assert.*;

import org.junit.Test;

public class SplitUtilTest {

	@Test
	public void testSplitEnglish() {
		assertArrayEquals(new Span[] {}, SplitUtil.split(""));
		assertArrayEquals(new Span[] {}, SplitUtil.split(" "));
		assertArrayEquals(new Span[] {}, SplitUtil.split(" () #"));
		assertArrayEquals(new Span[] { new Word("hello"), new Word("world") }, SplitUtil.split("hello,world"));
		assertArrayEquals(new Span[] { new Word("Hello"), new Word("World"), new Word("Wide") },
				SplitUtil.split(" Hello World&Wide"));
		assertArrayEquals(new Span[] { new Word("H"), new Word("M") }, SplitUtil.split("H&M"));
	}

	@Test
	public void testSplitChinese() {
		assertArrayEquals(new Span[] { new Phrase("你好") }, SplitUtil.split("你好"));
		assertArrayEquals(new Span[] { new Word("防"), new Word("毒"), new Phrase("软件") }, SplitUtil.split("防 毒 软件"));
		assertArrayEquals(new Span[] { new Phrase("防毒"), new Phrase("软件"), new Phrase("测试") },
				SplitUtil.split("防毒，软件？测试"));
		assertArrayEquals(new Span[] { new Phrase("我的电脑"), new Phrase("系统") }, SplitUtil.split("我的电脑，系统"));
	}

	@Test
	public void testSplitLongChinese() {
		assertArrayEquals(new Span[] { new Phrase("李白"), new Phrase("君不见黄河之水") }, SplitUtil.split("李白：君不见黄河之水天上来"));
	}

	@Test
	public void testSplitMixed() {
		assertArrayEquals(new Span[] { new Phrase("微软"), new Word("Microsoft"), new Phrase("发布了一"), new Word("款"),
				new Word("XBox"), new Phrase("游戏机") }, SplitUtil.split("微软Microsoft发布了一 款XBox游戏机"));
	}

}
