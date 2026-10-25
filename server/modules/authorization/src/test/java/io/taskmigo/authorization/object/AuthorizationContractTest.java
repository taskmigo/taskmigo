package io.taskmigo.authorization.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class AuthorizationContractTest {

    @Test
    void exposesDeclaredContractId() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("read");

        AuthorizationContract contract = method.getAnnotation(AuthorizationContract.class);

        assertThat(contract).isNotNull();
        assertThat(contract.value()).isEqualTo("test.objects.read-authz");
    }

    private static final class TestController {

        @AuthorizationContract("test.objects.read-authz")
        void read() {}
    }
}
