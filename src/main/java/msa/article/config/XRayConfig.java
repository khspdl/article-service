package msa.article.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.jakarta.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.plugins.EC2Plugin;
import com.amazonaws.xray.slf4j.SLF4JSegmentListener;
import com.amazonaws.xray.strategy.ContextMissingStrategy;
import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import com.amazonaws.xray.sql.TracingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URL;

@Configuration
public class XRayConfig {

    private static final Logger log = LoggerFactory.getLogger(XRayConfig.class);

    // === X-Ray 환경 설정 ===
    @Bean
    public AWSXRayRecorder awsXRayRecorder(XRayProps props) {
        try {
            URL ruleFileUrl = XRayConfig.class.getResource(props.samplingRulesJson());
            AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard()
                    .withPlugin(new EC2Plugin())
                    .withSamplingStrategy(new LocalizedSamplingStrategy(ruleFileUrl))
                    .withSegmentListener(new SLF4JSegmentListener(props.prefixLogName()))
                    .withContextMissingStrategy(new IgnoreContextMissingStrategy());

            AWSXRayRecorder recorder = builder.build();
            AWSXRay.setGlobalRecorder(recorder);
            return recorder;
        } catch (Exception e) {
            log.error("Failed to init AWS X-Ray recorder", e);
            throw e;
        }
    }

    // 요청 단위 세그먼트 오픈/클로즈는 필터가 처리
    @Bean
    public Filter tracingFilter(XRayProps props) {
        return new AWSXRayServletFilter(props.fixedSegmentName());
    }

    // === Hikari 설정 바인딩: aws.xray.datasource.* 를 HikariConfig에 바인딩 ===
    @Bean
    @ConfigurationProperties(prefix = "aws.xray.datasource")
    public HikariConfig hikariConfig() {
        return new HikariConfig();
    }

    // === DataSource 생성 후 X-Ray로 래핑 (순환참조 없음) ===
    @Bean
    @Primary
    public DataSource tracingDataSource(HikariConfig hikariConfig) {
        // 안전 가드: X-Ray 래핑 사용 시 일반 드라이버/URL을 쓰도록 유도
        if (hikariConfig.getDriverClassName() != null &&
                hikariConfig.getDriverClassName().startsWith("com.amazonaws.xray")) {
            throw new IllegalStateException("""
                Do not use X-Ray JDBC driver here.
                Use standard driver (e.g., com.mysql.cj.jdbc.Driver) and a normal JDBC URL (jdbc:mysql://...).
                The DataSource is already wrapped by TracingDataSource.
                """);
        }
        if (hikariConfig.getJdbcUrl() != null &&
                hikariConfig.getJdbcUrl().startsWith("jdbc:xray:")) {
            throw new IllegalStateException("""
                Do not use jdbc:xray: URL when using TracingDataSource.decorate(...).
                Use a normal URL like jdbc:mysql://...
                """);
        }

        HikariDataSource base = new HikariDataSource(hikariConfig);
        log.info("Wrapping HikariDataSource with AWS X-Ray TracingDataSource");
        return TracingDataSource.decorate(base);
    }

    // === X-Ray 프로퍼티 홀더 ===
    public record XRayProps(
            String fixedSegmentName,
            String prefixLogName,
            String samplingRulesJson
    ) {}

    @Bean
    @ConfigurationProperties(prefix = "aws.xray")
    public XRayProps xrayProps() {
        // spring-boot가 setter 없는 record에도 바인딩해줌 (Spring Boot 3.x)
        return new XRayProps(null, null, null);
    }

    // 컨텍스트 누락 시 예외 던지지 않는 전략
    static final class IgnoreContextMissingStrategy implements ContextMissingStrategy {
        @Override
        public void contextMissing(String message, Class<? extends RuntimeException> exceptionClass) {
            // no-op
        }
    }
}