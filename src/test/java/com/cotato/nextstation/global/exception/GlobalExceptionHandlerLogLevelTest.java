package com.cotato.nextstation.global.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 디스코드 알림은 ERROR 레벨만 전송하므로(logback-spring.xml), 5xx가 WARN으로 남으면 알림이 누락된다.
 */
class GlobalExceptionHandlerLogLevelTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private Logger logger;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUp() {
        logCapture = new ListAppender<>();
        logCapture.start();

        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logger.addAppender(logCapture);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logCapture);
    }

    private Level levelOfLastLog() {
        assertThat(logCapture.list).hasSize(1);
        return logCapture.list.get(0).getLevel();
    }

    @Test
    void 서버_오류_커스텀예외는_ERROR로_남는다() {
        handler.handleCustomException(new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR));

        assertThat(levelOfLastLog()).isEqualTo(Level.ERROR);
    }

    @Test
    void 클라이언트_오류_커스텀예외는_WARN으로_남는다() {
        handler.handleCustomException(new CustomException(GlobalErrorCode.NOT_FOUND));

        assertThat(levelOfLastLog()).isEqualTo(Level.WARN);
    }
}