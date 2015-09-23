/**
 * 
 */
package com.dianping.tiger.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yuantengkai
 * 任务分发类型注解,串行：chain
 * <mail to: zjytk05@163.com/>
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ExecuteType {
	String value();
}
