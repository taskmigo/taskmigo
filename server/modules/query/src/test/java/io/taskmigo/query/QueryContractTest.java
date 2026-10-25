package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class QueryContractTest {

    @Test
    void exposesDeclaredContractId() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list");

        QueryContract contract = method.getAnnotation(QueryContract.class);

        assertThat(contract).isNotNull();
        assertThat(contract.value()).isEqualTo("test.objects.list-query");
    }

    private static final class TestController {

        @QueryContract("test.objects.list-query")
        void list() {}
    }
}
