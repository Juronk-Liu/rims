package com.rims.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 经费额度：原子预留保证并发安全，且超支被拒、驳回释放占用。
 * 这一步把"不能超预算"从口头约定变成可回归的测试。
 */
class FinanceBudgetTest extends AbstractRimsApiTest {

    @Test
    void budgetReserveIsAtomicAndPreventsOverspend() throws Exception {
        long id = createActivatedProject(); // EQUIPMENT 预算 1000，已占用 0
        String admin = login("admin", "123456");

        // 1) 占用 600 成功
        String r1 = post(admin, "/api/projects/" + id + "/expenses",
                Map.of("category", "EQUIPMENT", "amount", new BigDecimal("600"), "purpose", "t1"));
        assertEquals(0, codeOf(r1));
        long e1Id = dataOf(r1).get("id").asLong();
        assertReserved(id, admin, "EQUIPMENT", "600");

        // 2) 再申请 600 -> 累计 1200 > 1000，必须被拒（原子预留的并发正确性体现）
        String r2 = post(admin, "/api/projects/" + id + "/expenses",
                Map.of("category", "EQUIPMENT", "amount", new BigDecimal("600"), "purpose", "t2"));
        assertNotEquals(0, codeOf(r2));
        // 不依赖中文字面量做断言（运行环境 file.encoding 可能为 GBK，中文常量比较不可靠）；
        // 超支消息里必然包含申请额 600 与剩余额 400 这两个 ASCII 数字，足以锁定“超支被拒”语义。
        String msg2 = objectMapper.readTree(r2).get("message").asText();
        assertTrue(msg2.contains("600") && msg2.contains("400"),
                "超支应被拒绝且提示包含申请额/剩余额，实际: [" + msg2 + "]");
        assertReserved(id, admin, "EQUIPMENT", "600"); // 占用未变

        // 3) 审批通过 e1，占用不变（已通过仍计入占用）
        ensureOk(admin, "/api/expenses/" + e1Id + "/approve", Map.of("approved", true));
        assertReserved(id, admin, "EQUIPMENT", "600");

        // 4) 再申请 200（pending），占用变为 800
        String r3 = post(admin, "/api/projects/" + id + "/expenses",
                Map.of("category", "EQUIPMENT", "amount", new BigDecimal("200"), "purpose", "t3"));
        assertEquals(0, codeOf(r3));
        long e3Id = dataOf(r3).get("id").asLong();
        assertReserved(id, admin, "EQUIPMENT", "800");

        // 5) 驳回 e3，占用回退到 600
        ensureOk(admin, "/api/expenses/" + e3Id + "/approve", Map.of("approved", false, "opinion", "测试驳回"));
        assertReserved(id, admin, "EQUIPMENT", "600");
    }

    private void assertReserved(long projectId, String token, String category, String expected) throws Exception {
        String resp = get(token, "/api/projects/" + projectId + "/budgets");
        assertEquals(0, codeOf(resp));
        JsonNode arr = dataOf(resp);
        BigDecimal actual = null;
        for (JsonNode n : arr) {
            if (category.equals(n.get("category").asText())) {
                actual = n.get("reservedAmount").decimalValue();
                break;
            }
        }
        assertTrue(actual != null, "未找到预算科目: " + category);
        BigDecimal exp = new BigDecimal(expected).stripTrailingZeros();
        assertEquals(0, exp.compareTo(actual.stripTrailingZeros()),
                "科目 " + category + " 预期占用 " + expected + "，实际 " + actual);
    }
}
