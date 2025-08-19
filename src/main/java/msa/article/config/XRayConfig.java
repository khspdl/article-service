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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driver;

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

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);

        LOG.info("Wrapping direct HikariDataSource with AWS X-Ray TracingDataSource");
        return TracingDataSource.decorate(ds);
    }
}