package msa.article.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.jakarta.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.sql.TracingDataSource;
import com.amazonaws.xray.strategy.sampling.CentralizedSamplingStrategy;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.ResourceUtils;

import javax.sql.DataSource;
import java.io.FileNotFoundException;
import java.net.URL;

@Configuration
public class XRayConfig {
    private static final Logger LOG = LoggerFactory.getLogger(XRayConfig.class);

    public XRayConfig() {
        try {
            URL ruleFile = ResourceUtils.getURL("classpath:xray/xray-sampling-rules.json");
            AWSXRayRecorder awsxRayRecorder = AWSXRayRecorderBuilder.standard()
                    .withDefaultPlugins()
                    .withSamplingStrategy(new CentralizedSamplingStrategy(ruleFile))
                    .build();

            AWSXRay.setGlobalRecorder(awsxRayRecorder);
        } catch (FileNotFoundException e) {
            LOG.error("XRay config file not found", e);
        }
    }

    @Bean
    public Filter tracingFilter() {
        return new AWSXRayServletFilter("articleservice");
    }

    /**
     * 직접 HikariDataSource 생성 + X-Ray wrapping
     * ECS 환경에서 환경변수 사용
     */
    @Bean
    @Primary
    public DataSource tracingDataSource() {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        String driver = System.getenv("DB_DRIVER_CLASS_NAME");

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);

        LOG.info("Wrapping direct HikariDataSource with AWS X-Ray TracingDataSource");
        return TracingDataSource.decorate(ds);
    }
}