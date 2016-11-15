package com.itranswarp.search;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchableField {

	/**
	 * Should the field be searchable.
	 * 
	 * @return Default true.
	 */
	boolean index() default true;

	boolean keyword() default false;

	/**
	 * Mapping field-level query time boosting.
	 * 
	 * @return Boosting value, default 1.0F.
	 */
	float boost() default 1.0F;
}
