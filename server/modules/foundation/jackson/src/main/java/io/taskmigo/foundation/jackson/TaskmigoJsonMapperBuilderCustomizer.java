package io.taskmigo.foundation.jackson;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.core.Ordered;
import tools.jackson.databind.json.JsonMapper;

final class TaskmigoJsonMapperBuilderCustomizer implements JsonMapperBuilderCustomizer, Ordered {

    @Override
    public void customize(JsonMapper.Builder jsonMapperBuilder) {
        TaskmigoJackson.configure(jsonMapperBuilder);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
