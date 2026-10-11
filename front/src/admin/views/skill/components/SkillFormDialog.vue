<template>
  <el-dialog
    :model-value="modelValue"
    title="新增Skill"
    width="560"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="80px">
      <el-form-item label="名称"><el-input placeholder="请输入Skill名称" /></el-form-item>
      <el-form-item label="类型">
        <el-select placeholder="请选择类型" style="width: 100%">
          <el-option label="对话增强" value="conversation" />
          <el-option label="诊断辅助" value="diagnosis" />
          <el-option label="情绪分析" value="emotion" />
        </el-select>
      </el-form-item>
      <el-form-item label="描述"><el-input type="textarea" :rows="3" placeholder="请输入描述" /></el-form-item>
      <el-form-item label="Prompt"><el-input type="textarea" :rows="4" placeholder="请输入Prompt模板" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="emit('update:modelValue', false)">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 新增 Skill 弹窗（纯展示表单，暂无后端提交逻辑，保持原行为）。
 */
defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
}>();
</script>
