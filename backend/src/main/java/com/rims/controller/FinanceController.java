package com.rims.controller;

import com.rims.common.PageResult;
import com.rims.common.Result;
import com.rims.dto.Dtos;
import com.rims.entity.BudgetItem;
import com.rims.entity.Expense;
import com.rims.service.FinanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/projects/{projectId}/budgets")
    public Result<List<BudgetItem>> budgets(@PathVariable Long projectId) {
        return Result.ok(financeService.budgets(projectId));
    }

    @PutMapping("/projects/{projectId}/budgets")
    public Result<List<BudgetItem>> saveBudgets(@PathVariable Long projectId,
                                                @RequestBody Dtos.BudgetSaveRequest req) {
        return Result.ok(financeService.saveBudgets(projectId, req));
    }

    @GetMapping("/projects/{projectId}/expenses")
    public Result<List<Expense>> expenses(@PathVariable Long projectId) {
        return Result.ok(financeService.listByProject(projectId));
    }

    @PostMapping("/projects/{projectId}/expenses")
    public Result<Expense> createExpense(@PathVariable Long projectId,
                                         @Valid @RequestBody Dtos.ExpenseRequest req) {
        return Result.ok(financeService.createExpense(projectId, req));
    }

    @GetMapping("/expenses")
    public Result<PageResult<Expense>> listExpenses(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long projectId) {
        return Result.ok(financeService.listExpenses(page, size, status, projectId));
    }

    @PostMapping("/expenses/{id}/approve")
    public Result<Expense> approve(@PathVariable Long id, @RequestBody Dtos.ExpenseApproveRequest req) {
        return Result.ok(financeService.approveExpense(id, req));
    }
}
