package com.rims.controller;

import com.rims.common.Result;
import com.rims.dto.Dtos;
import com.rims.entity.Achievement;
import com.rims.entity.Milestone;
import com.rims.entity.ProgressReport;
import com.rims.service.ProgressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/projects/{projectId}/milestones")
    public Result<List<Milestone>> milestones(@PathVariable Long projectId) {
        return Result.ok(progressService.listMilestones(projectId));
    }

    @PostMapping("/projects/{projectId}/milestones")
    public Result<Milestone> addMilestone(@PathVariable Long projectId,
                                          @Valid @RequestBody Dtos.MilestoneRequest req) {
        return Result.ok(progressService.addMilestone(projectId, req));
    }

    @PutMapping("/milestones/{id}")
    public Result<Milestone> updateMilestone(@PathVariable Long id,
                                             @Valid @RequestBody Dtos.MilestoneRequest req) {
        return Result.ok(progressService.updateMilestone(id, req));
    }

    @PostMapping("/milestones/{id}/complete")
    public Result<Milestone> completeMilestone(@PathVariable Long id) {
        return Result.ok(progressService.completeMilestone(id));
    }

    @DeleteMapping("/milestones/{id}")
    public Result<Void> deleteMilestone(@PathVariable Long id) {
        progressService.deleteMilestone(id);
        return Result.ok();
    }

    @GetMapping("/projects/{projectId}/progress")
    public Result<List<ProgressReport>> progress(@PathVariable Long projectId) {
        return Result.ok(progressService.listProgress(projectId));
    }

    @PostMapping("/projects/{projectId}/progress")
    public Result<ProgressReport> addProgress(@PathVariable Long projectId,
                                              @RequestBody Dtos.ProgressRequest req) {
        return Result.ok(progressService.addProgress(projectId, req));
    }

    @GetMapping("/projects/{projectId}/achievements")
    public Result<List<Achievement>> achievements(@PathVariable Long projectId) {
        return Result.ok(progressService.listAchievements(projectId));
    }

    @PostMapping("/projects/{projectId}/achievements")
    public Result<Achievement> addAchievement(@PathVariable Long projectId,
                                              @Valid @RequestBody Dtos.AchievementRequest req) {
        return Result.ok(progressService.addAchievement(projectId, req));
    }

    @DeleteMapping("/achievements/{id}")
    public Result<Void> deleteAchievement(@PathVariable Long id) {
        progressService.deleteAchievement(id);
        return Result.ok();
    }
}
