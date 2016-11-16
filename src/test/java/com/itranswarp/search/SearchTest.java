package com.itranswarp.search;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SearchTest {

	static final String INDEX = "testidx";

	static SearchableClient client;

	@Test
	public void testCRUDDoc() throws Exception {
		Tweet t1 = new Tweet("a-12345", "Michael Liao", Tweet.STYLE_A, true, "iPhone 7 Plus",
				"好消息，Java SE基础课程正式发布了！去www.liaoxuefeng.com看看。", System.currentTimeMillis());
		client.index(t1);
		Thread.sleep(5000);
		// get:
		Tweet t2 = client.get(Tweet.class, "a-12345");
		assertNotNull(t2);
		assertEquals("a-12345", t2.id);
		assertEquals("Michael Liao", t2.name);
		assertEquals(Tweet.STYLE_A, t2.style);
		assertTrue(t2.gender);
		assertEquals("iPhone 7 Plus", t2.via);
		assertNotNull(t2.content);
		assertTrue(t2.content.contains("Java SE"));
		assertEquals(System.currentTimeMillis(), t2.createdAt, 10000.0);
		// add another tweet but with same id:
		Tweet t3 = new Tweet("a-12345", "Bob Lee", Tweet.STYLE_B, false, "iPad mini",
				"好消息，Java EE课程正式发布了！去www.github.com看看。", System.currentTimeMillis());
		client.index(t3);
		// get doc:
		Tweet t4 = client.get(Tweet.class, "a-12345");
		assertNotNull(t4);
		assertEquals("a-12345", t4.id);
		assertEquals("Bob Lee", t4.name);
		assertEquals(Tweet.STYLE_B, t4.style);
		assertFalse(t4.gender);
		assertEquals("iPad mini", t4.via);
		assertNotNull(t4.content);
		assertTrue(t4.content.contains("Java EE"));
		assertEquals(System.currentTimeMillis(), t4.createdAt, 10000.0);
		// remove:
		client.unindex(Tweet.class, "a-12345");
		Thread.sleep(5000);
		// get doc:
		Tweet t5 = client.get(Tweet.class, "a-12345");
		assertNull(t5);
	}

	@Test
	public void testSearch() throws Exception {
		// build 10 docs:
		String[] authors = { "Michael Liao", "Bob Lee", "小明 & Trump", "小小明", "张sir", "李sir", "老王", "八神庵", "くさなぎ きょう",
				"不知火舞" };
		String[] contents = { "特朗普Trump上任后美或加入亚投行", "普京与特朗普电话会谈，称应联合打击恐怖主义", "厉害了：支持特朗普的创业者被从YC孵化器开除",
				"强推自家防毒软件，微软把卡巴斯基惹毛了", "神突破！Google发布神经网络机器翻译系统：支持中英",
				"Dive into the details of optimizing your cluster with the Elastic Cloud documentation.",
				"帅呆了！微软即将发布 Visual Studio for Mac 预览版", "Obama: Give Trump a chance", "Google自己做手机，三星开始给自家系统拉应用",
				"MIT开发新系统，让初学者也能处理复杂软件" };
		for (int i = 0; i < 10; i++) {
			Tweet t = new Tweet("id-" + i, authors[i], i % 2 == 0 ? Tweet.STYLE_A : Tweet.STYLE_B, i % 2 == 0,
					i % 2 == 0 ? "iPhone 7 Plus" : "网页", contents[i], i);
			client.index(t);
		}
		Thread.sleep(5000);
		// search:
		SearchResults<Tweet> sr1 = client.search(Tweet.class, "trump");
		sr1.results.stream().forEach(System.out::println);
		assertEquals(3, sr1.hits);
		System.out.println("====================");
		// search:
		SearchResults<Tweet> sr2 = client.search(Tweet.class, "微软");
		sr2.results.stream().forEach(System.out::println);
		assertEquals(2, sr2.hits);
		System.out.println("====================");
		SearchResults<Tweet> sr3 = client.search(Tweet.class, "trump特朗普");
		sr3.results.stream().forEach(System.out::println);
		assertEquals(4, sr3.hits);
		System.out.println("====================");
		SearchResults<Tweet> sr4 = client.search(Tweet.class, "普京与特朗普");
		sr4.results.stream().forEach(System.out::println);
		assertEquals(1, sr4.hits);
	}

	static Process process = null;

	@BeforeClass
	public static void startES() throws Exception {
		process = new ProcessBuilder("/srv/elasticsearch/bin/elasticsearch")
				.directory(new File("/srv/elasticsearch/bin/")).redirectErrorStream(true).start();
		InputStream input = process.getInputStream();
		final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
		for (;;) {
			String line = reader.readLine();
			if (line == null) {
				fail("Unexpected end of process.");
			}
			System.out.println(line);
			if (line.endsWith("] started")) {
				break;
			}
		}
		client = new SearchableClient();
		client.setBasePackage("com.itranswarp.search");
		client.init();
		client.createIndex();
		client.createMapping(Tweet.class);
		// continue do output:
		new Thread() {
			public void run() {
				try {
					for (;;) {
						String line = reader.readLine();
						if (line == null) {
							reader.close();
						} else {
							System.out.println(line);
						}
					}
				} catch (Exception e) {
					// ignore
				}
			}
		}.start();
	}

	@AfterClass
	public static void stopES() throws Exception {
		if (client != null) {
			client.close();
		}
		if (process != null) {
			process.destroy();
			process = null;
		}
	}

}
