package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    private static final Logger log = LoggerFactory.getLogger(TomcatConfig.class);

    /**
     * 운영(Ubuntu Linux)에서 Tomcat의 비동기 IO(useAsyncIO=true 기본값) 사용 시
     * flush()한 데이터가 응답 완료 전까지 소켓으로 전송되지 않아
     * SSE(text/event-stream)가 클라이언트에 0바이트로 무한 대기하는 문제 우회.
     * Windows 로컬에서는 재현되지 않는 플랫폼 의존적 동작으로, 클래식 NIO 폴러로 전환한다.
     */
    @Bean
    public TomcatConnectorCustomizer disableAsyncIo() {
        return connector -> {
            boolean applied = connector.setProperty("useAsyncIO", "false");
            log.info("[Tomcat] useAsyncIO=false 적용 결과: {}", applied);
        };
    }
}
