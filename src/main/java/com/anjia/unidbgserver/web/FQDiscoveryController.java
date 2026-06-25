package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.dto.*;
import com.anjia.unidbgserver.service.FQDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * FQ小说发现与分类控制器
 * 基于APK真实接口:
 *   GET /api/fqnovel/category/front    — 分类发现页(含热门分类、下拉分类、Tab等)
 *   GET /api/fqnovel/category/landing  — 分类下书籍列表(支持筛选排序)
 *   GET /api/fqnovel/category/cell     — 分类Cell内容区块
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/fqnovel/category", produces = MediaType.APPLICATION_JSON_VALUE)
public class FQDiscoveryController {

    @Autowired
    private FQDiscoveryService discoveryService;

    /**
     * 获取分类发现页
     * 返回热门分类、所有分类(Tab)、下拉分类列表、Tab内容等
     *
     * @param categoryTab Tab类型 (可选)
     * @param needHot     是否包含热门分类 (可选, 0/1)
     * @param source      来源标识 (可选)
     * @return 分类发现页数据
     */
    @GetMapping("/front")
    public CompletableFuture<FQNovelResponse<FQCategoryFrontResponse>> getCategoryFrontPage(
            @RequestParam(required = false) Integer categoryTab,
            @RequestParam(required = false) Boolean needHot,
            @RequestParam(required = false) String source) {
        if (log.isDebugEnabled()) {
            log.debug("获取分类发现页 - categoryTab: {}, needHot: {}, source: {}",
                    categoryTab, needHot, source);
        }
        return discoveryService.getCategoryFrontPage(categoryTab, needHot, source);
    }

    /**
     * 获取分类下书籍列表（分类落地页）
     *
     * @param categoryId    分类ID (必填)
     * @param offset        偏移量 (默认0)
     * @param limit         每页数量 (默认20，最大50)
     * @param subCategoryId 子分类ID (可选)
     * @param sortBy        排序方式 (可选: hot/new/overall)
     * @param bookStatus    书籍状态 (可选: serial/finish)
     * @param wordNumber    字数筛选 (可选: 0-30w/30w-50w/50w+)
     * @return 分类下书籍列表
     */
    @GetMapping("/landing")
    public CompletableFuture<FQNovelResponse<FQCategoryLandingResponse>> getCategoryLandingPage(
            @RequestParam String categoryId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String subCategoryId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String bookStatus,
            @RequestParam(required = false) String wordNumber) {

        if (log.isDebugEnabled()) {
            log.debug("获取分类书籍列表 - categoryId: {}, offset: {}, limit: {}, subCategoryId: {}, sortBy: {}",
                    categoryId, offset, limit, subCategoryId, sortBy);
        }
        if (categoryId == null || categoryId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                    FQNovelResponse.error("分类ID不能为空"));
        }
        return discoveryService.getCategoryLandingPage(
                categoryId.trim(), offset, limit,
                subCategoryId, sortBy, bookStatus, wordNumber);
    }

    /**
     * 获取分类Cell内容区块
     * 用于获取某个tab下的更多内容
     *
     * @param newCategoryTab Tab类型
     * @param reqType        请求类型
     * @param distinctStyle  去重样式
     * @return Cell区块数据
     */
    @GetMapping("/cell")
    public CompletableFuture<FQNovelResponse<FQCategoryCellResponse>> getCategoryCellData(
            @RequestParam(defaultValue = "0") int newCategoryTab,
            @RequestParam(defaultValue = "0") int reqType,
            @RequestParam(defaultValue = "0") int distinctStyle) {
        if (log.isDebugEnabled()) {
            log.debug("获取分类Cell数据 - newCategoryTab: {}, reqType: {}, distinctStyle: {}",
                    newCategoryTab, reqType, distinctStyle);
        }
        return discoveryService.getCategoryCellData(newCategoryTab, reqType, distinctStyle);
    }
}
