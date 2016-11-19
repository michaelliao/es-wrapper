package com.itranswarp.search;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

/**
 * A searchable client.
 * 
 * @author Michael Liao
 */
public class SearchableClient implements AutoCloseable {

	private final Log log = LogFactory.getLog(getClass());

	private String basePackage;
	private String index = "default";
	private String host = "localhost";
	private int port = 9300;
	private int maxResults = 100;

	private Client client;
	private Map<Class<?>, Mapping> mappings = new HashMap<>();

	public void setBasePackage(String basePackage) {
		this.basePackage = basePackage;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setMaxResults(int maxResults) {
		this.maxResults = maxResults;
	}

	public <T> SearchResults<T> search(Class<T> clazz, String words) {
		return search(clazz, words, 0.5f);
	}

	public <T> SearchResults<T> search(Class<T> clazz, String words, float minScore) {
		Mapping mapping = getMappingFromClass(clazz);
		Span[] spans = SplitUtil.split(words);
		if (spans.length == 0) {
			return null;
		}
		QueryBuilder queryBuilder = null;
		if (spans.length == 1) {
			queryBuilder = createQueryBuilder(spans[0]);
		} else {
			BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
			for (Span span : spans) {
				boolQueryBuilder.should(createQueryBuilder(span));
			}
			queryBuilder = boolQueryBuilder;
		}
		SearchResponse sr = client.prepareSearch(index).setQuery(queryBuilder).setSize(maxResults).get();
		SearchHits shs = sr.getHits();
		long total = shs.getTotalHits();
		SearchHit[] hs = shs.getHits();
		List<T> results = new ArrayList<>(maxResults);
		for (int i = 0; i < hs.length; i++) {
			SearchHit sh = hs[i];
			if (sh.getScore() >= minScore) {
				Map<String, Object> props = sh.getSource();
				results.add(mapping.createBean(sh.getId(), props));
			} else {
				break;
			}
		}
		return new SearchResults<>(total, results);
	}

	QueryBuilder createQueryBuilder(Span span) {
		if (span instanceof Word) {
			return QueryBuilders.termQuery("_all", span.text);
		}
		if (span instanceof Phrase) {
			if (span.text.length() > 3) {
				return QueryBuilders.multiMatchQuery(span.text, "_all").minimumShouldMatch("75%");
			} else {
				return QueryBuilders.matchPhraseQuery("_all", span.text);
			}
		}
		throw new IllegalArgumentException("Unsupported type of Span: " + span.getClass().getName());
	}

	/**
	 * Get document by id.
	 * 
	 * @param clazz
	 *            Class of document.
	 * @param id
	 *            Document id.
	 * @return Java bean.
	 */
	public <T> T get(Class<T> clazz, String id) {
		Mapping mapping = getMappingFromClass(clazz);
		GetResponse gr = client.prepareGet(index, mapping.getType(), id).get();
		if (!gr.isExists()) {
			return null;
		}
		Map<String, Object> props = gr.getSourceAsMap();
		return mapping.createBean(id, props);
	}

	/**
	 * Index a searchable bean.
	 * 
	 * @param bean
	 *            Searchable bean.
	 */
	public <T> void index(T bean) {
		Mapping mapping = getMappingFromBean(bean);
		IndexResponse ir = client.prepareIndex(index, mapping.getType(), mapping.getId(bean))
				.setSource(mapping.getSource(bean)).get();
		log.info("Type " + mapping.getType() + "@" + ir.getId() + " indexed.");
	}

	/**
	 * Unindex a searchable bean.
	 * 
	 * @param bean
	 *            Searchable bean.
	 */
	public <T> void unindex(T bean) {
		Mapping mapping = getMappingFromBean(bean);
		DeleteResponse dr = client.prepareDelete(index, mapping.getType(), mapping.getId(bean)).get();
		log.info("Type " + mapping.getType() + "@" + dr.getId() + " unindexed.");
	}

	/**
	 * Unindex a searchable bean.
	 * 
	 * @param clazz
	 *            Class type.
	 * @param id
	 *            Id as string.
	 */
	public <T> void unindex(Class<T> clazz, String id) {
		Mapping mapping = getMappingFromClass(clazz);
		DeleteResponse dr = client.prepareDelete(index, mapping.getType(), id).get();
		log.info("Type " + mapping.getType() + "@" + dr.getId() + " unindexed.");
	}

	Mapping getMappingFromBean(Object bean) {
		if (bean == null) {
			throw new IllegalArgumentException("Argument cannot be null.");
		}
		Mapping mapping = mappings.get(bean.getClass());
		if (mapping == null) {
			throw new IllegalArgumentException(
					"Class " + bean.getClass().getName() + " is not defined in searchable packages.");
		}
		return mapping;
	}

	Mapping getMappingFromClass(Class<?> clazz) {
		if (clazz == null) {
			throw new IllegalArgumentException("Argument cannot be null.");
		}
		Mapping mapping = mappings.get(clazz);
		if (mapping == null) {
			throw new IllegalArgumentException("Class " + clazz.getName() + " is not defined in searchable packages.");
		}
		return mapping;
	}

	@SuppressWarnings("resource")
	public void init() throws IOException {
		log.info("Init client...");
		this.client = new PreBuiltTransportClient(Settings.EMPTY)
				.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
		// check doc types:
		List<Class<?>> docTypes = ClassUtil.scan(basePackage, (c) -> {
			return c.isAnnotationPresent(SearchableDocument.class);
		});
		for (Class<?> docType : docTypes) {
			this.mappings.put(docType, new Mapping(docType));
		}
	}

	public boolean createMapping(Class<?> docType) {
		Mapping mapping = getMappingFromClass(docType);
		IndicesAdminClient idc = client.admin().indices();
		GetMappingsResponse gmr = idc.getMappings(new GetMappingsRequest()).actionGet();
		ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings = gmr.getMappings();
		if (mappings.containsKey(mapping.getType())) {
			log.info("Found mapping for class " + docType.getName() + ".");
			return false;
		}
		log.info("Mapping not found for class " + docType.getName() + ". Auto-create...");
		PutMappingResponse pmr = idc.preparePutMapping(index).setType(mapping.getType()).setSource(mapping.getSource())
				.get();
		if (!pmr.isAcknowledged()) {
			throw new RuntimeException("Failed to create mapping for class:" + docType.getName() + ".");
		}
		return true;
	}

	/**
	 * Create index.
	 * 
	 * @return True if index create ok, false if index is already exist.
	 */
	public boolean createIndex() {
		// check index exist:
		IndicesAdminClient idc = client.admin().indices();
		IndicesExistsResponse ier = idc.exists(new IndicesExistsRequest(index)).actionGet();
		if (!ier.isExists()) {
			log.info("Index not found. Auto-create...");
			// create index:
			CreateIndexResponse cir = idc.create(new CreateIndexRequest(index)).actionGet();
			if (!cir.isAcknowledged()) {
				throw new RuntimeException("Failed to create index.");
			}
			return true;
		}
		return false;
	}

	@Override
	public void close() {
		if (client != null) {
			client.close();
			client = null;
		}
	}

}
