package com.anjia.unidbgserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 新分类系统首页(发现页)响应
 * 对应 fqnovel: /reading/bookapi/new_category/front/v
 * response: {code, message, data: {rule, down_category[], hot_category[], category_tab_config, category_tab_data}}
 */
@Data
public class FQCategoryFrontResponse {

    /** 分类页规则 */
    private Object rule;

    /** 下拉分类列表(所有分类) */
    @JsonProperty("down_category")
    private List<CategoryItem> downCategory;

    /** 热门分类列表 */
    @JsonProperty("hot_category")
    private List<CategoryItem> hotCategory;

    /** Tab配置 */
    @JsonProperty("category_tab_config")
    private CategoryTabConfig categoryTabConfig;

    /** Tab内容数据 */
    @JsonProperty("category_tab_data")
    private CategoryTabData categoryTabData;

    @Data
    public static class CategoryItem {
        /** 分类ID */
        private String id;

        /** 分类名称 */
        private String name;

        /** 书籍数量 */
        @JsonProperty("book_num")
        private String bookNum;

        /** 分类封面图 */
        private String cover;

        /** 分类描述 */
        private String description;

        /** 子分类(GetCategoryNewListCategoryData) */
        private List<CategoryItem> top;

        /** 分类标签 */
        private String tag;

        /** 尺寸/dim */
        private String dim;

        /** 顶部标记 */
        @JsonProperty("top_mark")
        private String topMark;

        /** 流派类型 */
        @JsonProperty("genre_type")
        private Integer genreType;

        /** 书籍类型 */
        @JsonProperty("book_type")
        private Integer bookType;

        /** 推荐组ID */
        @JsonProperty("recommend_group_id")
        private String recommendGroupId;

        /** 推荐信息 */
        @JsonProperty("recommend_info")
        private String recommendInfo;
    }

    @Data
    public static class CategoryTabConfig {
        @JsonProperty("tab_type_list")
        private List<Integer> tabTypeList;

        @JsonProperty("tab_name_list")
        private List<String> tabNameList;

        @JsonProperty("default_tab")
        private Integer defaultTab;

        @JsonProperty("tab_list")
        private List<TabItem> tabList;
    }

    @Data
    public static class TabItem {
        @JsonProperty("tab_type")
        private Integer tabType;

        @JsonProperty("tab_name")
        private String tabName;
    }

    @Data
    public static class CategoryTabData {
        @JsonProperty("category_tab")
        private Integer categoryTab;

        @JsonProperty("tab_name")
        private String tabName;

        @JsonProperty("cell_data")
        private List<Object> cellData;

        @JsonProperty("hide_side_bar")
        private Boolean hideSideBar;

        @JsonProperty("sub_tabs")
        private List<SubTabItem> subTabs;

        @JsonProperty("selected_sub_tab")
        private String selectedSubTab;
    }

    @Data
    public static class SubTabItem {
        @JsonProperty("sub_tab_id")
        private String subTabId;

        @JsonProperty("sub_tab_name")
        private String subTabName;
    }
}
