package com.example.sub;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Description:
 * <p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Trace {

}
