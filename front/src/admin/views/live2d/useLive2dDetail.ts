import { computed, onMounted, reactive, ref, shallowRef } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getLive2dModelDetail,
  updateLive2dModel,
  enableLive2dModel,
  deleteLive2dModel,
} from "@/admin/api/live2d/config";
import type { Live2dConfigAdminVO, Live2dConfigUpdateDTO } from "@/admin/api/live2d/config";
import type { EditForm } from "./useLive2dList";

/**
 * Live2D 模型详情页全部业务逻辑（单一职责）：
 * - 详情加载（skeleton / 失败重载 三态）
 * - 编辑（复用 Live2dEditDialog 弹窗保存）
 * - 启用/禁用切换（后端全局唯一互斥）
 * - 删除（确认后返回列表）
 */
export function useLive2dDetail() {
  const route = useRoute();
  const router = useRouter();
  const modelId = String(route.params.id ?? "");

  /* ==================== 页面状态 ==================== */

  const pageLoading = ref(true);
  const pageError = ref(false);
  const detail = shallowRef<Live2dConfigAdminVO | null>(null);

  /* ==================== 编辑弹窗状态 ==================== */

  const editVisible = ref(false);
  const editSubmitting = ref(false);
  const editForm = reactive<EditForm>({
    id: null,
    modelName: "",
    aliasName: "",
    coverImagePath: "",
    sort: 0,
    remark: "",
  });

  /* ==================== 白名单中文标签编辑状态 ==================== */

  const labelEditorVisible = ref(false);
  const labelSaving = ref(false);

  /* ==================== 派生状态 ==================== */

  const enabledTagType = computed<"success" | "info">(() =>
    detail.value?.enabled === 1 ? "success" : "info",
  );
  const enabledLabel = computed(() => (detail.value?.enabled === 1 ? "启用" : "禁用"));

  /* ==================== 语义提示词内联编辑状态 ==================== */

  const promptText = ref("");
  const savingPrompt = ref(false);

  /* ==================== 数据加载 ==================== */

  function applyDetail(data: Live2dConfigAdminVO) {
    Object.assign(editForm, {
      id: data.id ?? null,
      modelName: data.modelName ?? "",
      aliasName: data.aliasName ?? "",
      coverImagePath: data.coverImagePath ?? "",
      sort: data.sort ?? 0,
      remark: data.remark ?? "",
    });
    promptText.value = data.semanticPrompt ?? "";
  }

  async function loadDetail() {
    pageLoading.value = true;
    pageError.value = false;
    try {
      const res = await getLive2dModelDetail(modelId);
      const data = res.data.data;
      detail.value = data ?? null;
      if (!data) {
        pageError.value = true;
        return;
      }
      applyDetail(data);
    } catch (e) {
      pageError.value = true;
      ElMessage.error((e as Error)?.message || "模型详情加载失败");
    } finally {
      pageLoading.value = false;
    }
  }

  function handleReload() {
    loadDetail();
  }

  /* ==================== 编辑 ==================== */

  function handleEdit() {
    if (!detail.value) return;
    Object.assign(editForm, {
      id: detail.value.id ?? null,
      modelName: detail.value.modelName ?? "",
      aliasName: detail.value.aliasName ?? "",
      coverImagePath: detail.value.coverImagePath ?? "",
      sort: detail.value.sort ?? 0,
      remark: detail.value.remark ?? "",
    });
    editVisible.value = true;
  }

  async function handleEditSubmit() {
    if (editForm.id == null) {
      ElMessage.error("缺少模型ID，无法更新");
      return;
    }
    editSubmitting.value = true;
    try {
      const dto: Live2dConfigUpdateDTO = {
        id: editForm.id,
        modelName: editForm.modelName.trim() || undefined,
        // 别名：空串提交 undefined（后端 setIgnoreNullValue：null 不覆盖旧值），不传则保持原值
        aliasName: editForm.aliasName.trim() || undefined,
        // 封面走图片上传；空串显式提交以允许清空封面（后端 setIgnoreNullValue：null 不覆盖，空串覆盖）
        coverImagePath: editForm.coverImagePath.trim() || "",
        sort: editForm.sort,
        remark: editForm.remark.trim() || undefined,
      };
      // 启用态走「启用/禁用」按钮（后端全局唯一互斥）、语义提示词走详情页内联编辑，均不在弹窗提交
      await updateLive2dModel(editForm.id, dto);
      ElMessage.success("模型配置已更新");
      editVisible.value = false;
      await loadDetail();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "模型配置更新失败，请稍后重试");
    } finally {
      editSubmitting.value = false;
    }
  }

  /* ==================== 语义提示词内联编辑 ==================== */

  function handleCancelPrompt() {
    promptText.value = detail.value?.semanticPrompt ?? "";
  }

  async function handleSavePrompt() {
    if (editForm.id == null && detail.value?.id != null) {
      // 兜底：以详情 id 为准
      editForm.id = detail.value.id;
    }
    const id = editForm.id ?? detail.value?.id;
    if (id == null) {
      ElMessage.error("缺少模型ID，无法保存提示词");
      return;
    }
    savingPrompt.value = true;
    try {
      await updateLive2dModel(id, { id, semanticPrompt: promptText.value.trim() });
      ElMessage.success("语义提示词已保存");
      await loadDetail();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "语义提示词保存失败，请稍后重试");
    } finally {
      savingPrompt.value = false;
    }
  }

  /* ==================== 白名单中文标签（AssetLabelEditor） ==================== */

  function handleOpenLabelEditor() {
    labelEditorVisible.value = true;
  }

  async function handleSaveLabels(payload: {
    expressionList: Record<string, string>;
    motionList: Record<string, string>;
  }) {
    const id = editForm.id ?? detail.value?.id;
    if (id == null) {
      ElMessage.error("缺少模型ID，无法保存标签");
      return;
    }
    labelSaving.value = true;
    try {
      await updateLive2dModel(id, { id, expressionList: payload.expressionList, motionList: payload.motionList });
      ElMessage.success("中文含义已保存");
      labelEditorVisible.value = false;
      await loadDetail();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "标签保存失败，请稍后重试");
    } finally {
      labelSaving.value = false;
    }
  }

  /* ==================== 启用 / 禁用 ==================== */

  async function handleStatusToggle() {
    const row = detail.value;
    if (!row || row.id == null) {
      ElMessage.error("缺少模型ID，无法切换启用状态");
      return;
    }
    const next = row.enabled === 1 ? 0 : 1;
    try {
      await enableLive2dModel(row.id, next);
      const displayName = row.aliasName || row.modelName || "";
      ElMessage.success(next === 1 ? `「${displayName}」已启用` : `「${displayName}」已禁用`);
      await loadDetail();
    } catch (e) {
      ElMessage.error((e as Error)?.message || "启用状态更新失败，请稍后重试");
    }
  }

  /* ==================== 删除 ==================== */

  async function handleDelete() {
    const row = detail.value;
    if (!row || row.id == null) {
      ElMessage.error("缺少模型ID，无法删除");
      return;
    }
    try {
      await ElMessageBox.confirm(
        `确定删除模型「${row.aliasName || row.modelName || ""}」吗？删除后不可恢复。`,
        "删除确认",
        {
          type: "warning",
          confirmButtonText: "删除",
          cancelButtonText: "取消",
          confirmButtonClass: "el-button--danger",
        },
      );
    } catch {
      return; // 用户取消
    }
    try {
      await deleteLive2dModel(row.id);
      ElMessage.success("模型已删除");
      router.replace("/admin/live2d");
    } catch (e) {
      ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
    }
  }

  onMounted(() => {
    loadDetail();
  });

  return {
    // 页面状态
    pageLoading,
    pageError,
    detail,
    // 编辑
    editVisible,
    editSubmitting,
    editForm,
    handleEdit,
    handleEditSubmit,
    // 白名单中文标签
    labelEditorVisible,
    labelSaving,
    handleOpenLabelEditor,
    handleSaveLabels,
    // 语义提示词
    promptText,
    savingPrompt,
    handleCancelPrompt,
    handleSavePrompt,
    // 派生
    enabledTagType,
    enabledLabel,
    // 操作
    handleReload,
    handleStatusToggle,
    handleDelete,
  };
}

export type UseLive2dDetail = ReturnType<typeof useLive2dDetail>;
