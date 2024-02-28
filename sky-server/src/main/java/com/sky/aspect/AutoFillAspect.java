package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面
 * 实现公共字段自动填充处理逻辑
 */

@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    // 切入点（定义对哪些类的哪些方法进行拦截）：拦截所有mapper包下的带有@AutoFill注解的方法
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut() {
    }


    // 通知（对代码增强的具体逻辑）

    /**
     * 前置通知：在目标方法执行前执行
     * 在通知中进行公共字段的赋值
     * @param joinPoint
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始进行公共字段的自动填充...");

        // 获取当前被拦截方法上的数据库操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature(); // 获取方法签名对象
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);// 获取方法上的注解对象
        OperationType operationType = autoFill.value(); // 获取注解对象上的value属性值，即数据库操作类型

        // 获取当前被拦截方法的参数——实体对象
        Object[] args = joinPoint.getArgs();    // 获取目标方法的所有参数
        if (args == null || args.length == 0) { // 防止出现空指针，一般不会出现
            log.error("被拦截方法没有参数，无法进行公共字段的自动填充");
            return;
        }
        Object entity = args[0];    // 获取第一个参数，即实体对象（约定要使用公共字段自动填充的方法必须将实体参数放在第一个）

        // 准备要赋的公共字段的值
        LocalDateTime now = LocalDateTime.now();    // 获取当前时间
        Long currentId = BaseContext.getCurrentId();    // 获取当前登录用户的id

        // 根据数据库操作类型，为对应的属性通过反射来赋值
        if (operationType == OperationType.INSERT) {    // 插入操作
            // 为4个公共字段赋值
            try {
                // 获取对象公共字段的各个set方法，为了防止拼写方法名错误，也用定义好的常量
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                // 通过反射为对象属性赋值
                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (operationType == OperationType.UPDATE) { // 更新操作
            // 为2个公共字段赋值
            try {
                // 获取对象公共字段的各个set方法
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);
                // 通过反射为对象属性赋值
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}


