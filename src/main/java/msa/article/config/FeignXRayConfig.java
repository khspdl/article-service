package msa.article.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.TraceHeader;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignXRayConfig {

    @Bean
    public RequestInterceptor xrayTraceInterceptor() {
        return requestTemplate -> {
            Entity currentEntity = AWSXRay.getCurrentSegmentOptional().orElse(null);
            if (currentEntity != null) {
                String traceHeader = TraceHeader.fromEntity(currentEntity).toString();
                requestTemplate.header("X-Amzn-Trace-Id", traceHeader);
            }
        };
    }
}