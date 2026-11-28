package io.taskmigo.eav.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.query.QueryOperator;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EavQueryFieldTest {

    @Test
    @DisplayName("query capabilities depend only on the stable declared data type")
    void shouldDescribeTypedCapabilities() {
        for (EavDataType type : EavDataType.values()) {
            EavQueryField field = EavQueryField.describe(UUID.randomUUID(), "value", type, true);
            assertThat(field.nullable()).isTrue();
            assertThat(field.operators()).contains(QueryOperator.EQ, QueryOperator.NE);
            switch (type) {
                case STRING -> {
                    assertThat(field.sortable()).isTrue();
                    assertThat(field.operators()).containsExactlyInAnyOrder(
                        QueryOperator.EQ,
                        QueryOperator.NE,
                        QueryOperator.CONTAINS
                    );
                }
                case NUMBER, DATE, DATETIME -> {
                    assertThat(field.sortable()).isTrue();
                    assertThat(field.operators()).containsExactlyInAnyOrder(
                        QueryOperator.EQ,
                        QueryOperator.NE,
                        QueryOperator.GT,
                        QueryOperator.GTE,
                        QueryOperator.LT,
                        QueryOperator.LTE
                    );
                }
                case BOOLEAN, UUID, ENUM -> {
                    assertThat(field.sortable()).isFalse();
                    assertThat(field.operators()).containsExactlyInAnyOrder(QueryOperator.EQ, QueryOperator.NE);
                }
            }
        }
    }
}
