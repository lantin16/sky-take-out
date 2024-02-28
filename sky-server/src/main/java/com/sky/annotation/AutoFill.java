package com.sky.annotation;

import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解AutoFill
 * 用于标识需要进行公共字段(create_time, create_user, update_time, update_user)自动填充的方法
 * 加在Mapper的方法上
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AutoFill {

    // 枚举数据库操作类型：UPDATE、INSERT
    OperationType value();
}
