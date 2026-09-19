package com.company.myweb.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static com.company.myweb.constant.ProjectConstant.*;

@Slf4j
@Component
@Aspect
public class LoggerAspect {

    /**
     * 排除全域例外處理器，避免 CGLIB 代理警告與重複記錄。
     */
    @Pointcut("!within(com.company.myweb.rest.GlobalExceptionRestController)")
    public void notRestExceptionHandler() {
    }

    /**
     * 只攔截 Controller、REST 與 Service 的方法。
     */
    @Pointcut("(execution(* com.company.myweb.controller..*.*(..)) "
            + "|| execution(* com.company.myweb.rest..*.*(..)) "
            + "|| execution(* com.company.myweb.service..*.*(..))) "
            + "&& notRestExceptionHandler()")
    public void businessLayer() {
    }

    /**
     * 全 package 例外切點，供 @AfterThrowing 記錄各層錯誤。
     */
    @Pointcut("execution(* com.company.myweb..*.*(..)) && notRestExceptionHandler()")
    public void anyMyWebMethod() {
    }

    /**
     * 記錄業務方法的參數、執行時間與回傳值。
     */
    @Around("businessLayer()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 取得方法簽名、參數與目標 Bean 類別。
        String method = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        String targetClass = joinPoint.getTarget().getClass().getSimpleName();

        log.info(ANSI_PINK + "[{}] {} 開始執行，參數 {}" + ANSI_RESET, targetClass, method, Arrays.toString(args));

        Instant startTime = Instant.now();
        // 執行原始方法；此處沿用原始參數。
        Object result = joinPoint.proceed(args);
        long elapsedMs = Duration.between(startTime, Instant.now()).toMillis();

        log.info(ANSI_PINK + "[{}] {} 執行結束，耗時 {} ms，回傳 {}" + ANSI_RESET, targetClass, method, elapsedMs, result);
        return result;
    }

    /**
     * 記錄 com.company.myweb 各層方法拋出的例外與完整 stack trace。
     */
    @AfterThrowing(value = "anyMyWebMethod()", throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Exception ex) {
        String method = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        Throwable cause = ex.getCause();

        // 最後一個 Throwable 會由 SLF4J 印出完整 stack trace。
        log.error(ANSI_RED + "{} 拋出例外，參數 {}，訊息 [{}]，根本原因 [{}]" + ANSI_RESET,
                method,
                Arrays.toString(args),
                ex.getMessage(),
                cause != null ? cause.getMessage() : "無",
                ex);
    }
}
