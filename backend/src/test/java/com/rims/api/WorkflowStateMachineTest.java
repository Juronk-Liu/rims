package com.rims.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 状态机与权限：研究者不能审部门环节、非法状态流转被拒、admin 可推进到立项。 */
class WorkflowStateMachineTest extends AbstractRimsApiTest {

    @Test
    void researcherCannotReviewAtDeptLevel() throws Exception {
        long id = createSubmittedProject(); // SUBMITTED
        String zhang = login("zhangsan", "123456"); // RESEARCHER
        String resp = post(zhang, "/api/projects/" + id + "/review", Map.of("action", "APPROVE"));
        assertEquals(403, codeOf(resp));
    }

    @Test
    void illegalTransitionIsRejected() throws Exception {
        String admin = login("admin", "123456");
        // 种子数据 p5（id=5）为已结题，对终态项目提交申报应被拒
        String resp = post(admin, "/api/projects/5/submit", null);
        assertEquals(400, codeOf(resp));
    }

    @Test
    void adminCanDriveProjectToInProgress() throws Exception {
        long id = createActivatedProject();
        String admin = login("admin", "123456");
        String detail = get(admin, "/api/projects/" + id);
        assertEquals(0, codeOf(detail));
        JsonNode project = dataOf(detail).get("project");
        assertEquals("IN_PROGRESS", project.get("status").asText());
    }
}
