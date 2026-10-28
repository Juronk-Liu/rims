package com.rims.controller;

import com.rims.common.PageResult;
import com.rims.common.Result;
import com.rims.dto.Dtos;
import com.rims.entity.Project;
import com.rims.service.AiReviewService;
import com.rims.service.ProjectService;
import com.rims.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final WorkflowService workflowService;
    private final AiReviewService aiReviewService;

    public ProjectController(ProjectService projectService, WorkflowService workflowService,
                             AiReviewService aiReviewService) {
        this.projectService = projectService;
        this.workflowService = workflowService;
        this.aiReviewService = aiReviewService;
    }

    @GetMapping
    public Result<PageResult<Dtos.ProjectListItem>> list(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String category,
                                                         @RequestParam(defaultValue = "false") boolean onlyMine) {
        return Result.ok(projectService.list(page, size, keyword, status, category, onlyMine));
    }

    @GetMapping("/{id}")
    public Result<Dtos.ProjectDetailVO> detail(@PathVariable Long id) {
        return Result.ok(projectService.detail(id));
    }

    @PostMapping
    public Result<Project> create(@Valid @RequestBody Dtos.ProjectSaveRequest req) {
        return Result.ok(projectService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @Valid @RequestBody Dtos.ProjectSaveRequest req) {
        return Result.ok(projectService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/submit")
    public Result<Project> submit(@PathVariable Long id) {
        return Result.ok(workflowService.submit(id));
    }

    @PostMapping("/{id}/review")
    public Result<Project> review(@PathVariable Long id, @Valid @RequestBody Dtos.ReviewRequest req) {
        return Result.ok(workflowService.review(id, req));
    }

    @PostMapping("/{id}/acceptance-submit")
    public Result<Project> submitAcceptance(@PathVariable Long id) {
        return Result.ok(workflowService.submitAcceptance(id));
    }

    @PostMapping("/{id}/acceptance")
    public Result<Project> acceptance(@PathVariable Long id, @RequestBody Dtos.AcceptanceRequest req) {
        return Result.ok(workflowService.acceptance(id, req));
    }

    @PostMapping("/{id}/terminate")
    public Result<Project> terminate(@PathVariable Long id, @RequestBody Dtos.ReviewRequest req) {
        return Result.ok(workflowService.terminate(id, req.opinion()));
    }

    @PostMapping("/{id}/ai-review")
    public Result<Dtos.AiReviewResponse> aiReview(@PathVariable Long id) {
        return Result.ok(aiReviewService.review(id));
    }
}
