package com.itranswarp.search;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Mapping {
	final String type;
	final Class<?> clazz;
	final Field id;
	final Map<String, Field> fields;
	final Map<String, Map<String, String>> mapping;

	public Mapping(Class<?> clazz) {
		this.clazz = clazz;
		// scan for id and fields:
		Field id = null;
		Map<String, Field> fields = new HashMap<>();
		Map<String, Map<String, String>> mapping = new HashMap<>();
		for (Field f : clazz.getFields()) {
			SearchableField sf = f.getAnnotation(SearchableField.class);
			if (f.isAnnotationPresent(SearchableId.class)) {
				if (sf != null) {
					throw new IllegalArgumentException("Cannot use both @SearchableId and @SearchableField.");
				}
				if (f.getType() != String.class) {
					throw new IllegalArgumentException("@SearchableId field can only be String.");
				}
				id = f;
			} else if (sf != null) {
				mapping.put(f.getName(), of("type", sf.keyword() ? "keyword" : getFieldType(f)));
				fields.put(f.getName(), f);
			}
		}
		if (id == null) {
			throw new IllegalArgumentException("@SearchableId not found in class: " + clazz.getName());
		}
		// init:
		this.id = id;
		this.fields = fields;
		this.mapping = mapping;
		this.type = Character.toLowerCase(clazz.getSimpleName().charAt(0)) + clazz.getSimpleName().substring(1);
	}

	<K, V> Map<K, V> of(K key, V value) {
		return Collections.singletonMap(key, value);
	}

	public String getType() {
		return type;
	}

	public String getSource() {
		Map<String, Object> map = of("properties", this.mapping);
		return JsonUtil.toJson(map);
	}

	String getFieldType(Field f) {
		String type = fieldTypes.get(f.getType());
		if (type == null) {
			throw new IllegalArgumentException("Field " + this.clazz.getName() + "." + f.getName()
					+ " type is unsupported: " + f.getType().getName());
		}
		return type;
	}

	private static Map<Class<?>, String> fieldTypes;

	static {
		Map<Class<?>, String> map = new HashMap<>();
		map.put(String.class, "text");
		map.put(int.class, "integer");
		map.put(Integer.class, "integer");
		map.put(long.class, "long");
		map.put(Long.class, "long");
		map.put(boolean.class, "boolean");
		map.put(Boolean.class, "boolean");
		map.put(float.class, "float");
		map.put(Float.class, "float");
		map.put(double.class, "double");
		map.put(Double.class, "double");
		fieldTypes = map;
	}

	/**
	 * Get id value from a bean.
	 * 
	 * @param bean
	 *            Target java bean.
	 * @return Id as string.
	 */
	public String getId(Object bean) {
		try {
			return this.id.get(bean).toString();
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, Object> getSource(Object bean) {
		try {
			Map<String, Object> map = new HashMap<>();
			for (Map.Entry<String, Field> entry : this.fields.entrySet()) {
				map.put(entry.getKey(), entry.getValue().get(bean));
			}
			return map;
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public <T> T createBean(String idValue, Map<String, Object> props) {
		try {
			@SuppressWarnings("unchecked")
			T bean = (T) clazz.newInstance();
			for (Map.Entry<String, Field> entry : this.fields.entrySet()) {
				String name = entry.getKey();
				Object value = props.get(name);
				Field field = entry.getValue();
				field.set(bean, value);
			}
			this.id.set(bean, idValue);
			return bean;
		} catch (IllegalAccessException | InstantiationException e) {
			throw new RuntimeException(e);
		}
	}

}
