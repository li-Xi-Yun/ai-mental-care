<template>
  <div class="info-card idle-config-card">
    <div class="card-title">
      待机配置
      <el-tag v-if="!enabled && idleAutoPlay !== 1" size="small" type="info" effect="light">未启用</el-tag>
      <el-tag v-else-if="idleAutoPlay === 1" size="small" type="warning" effect="light">自动待机（库 Idle）</el-tag>
      <el-tag v-else size="small" type="success" effect="light">已启用 · 每 {{ idleIntervalSeconds }}s</el-tag>
    </div>

    <div class="auto-play-row">
      <el-switch
        :model-value="idleAutoPlay === 1"
        size="small"
        @update:model-value="(v) => emit('update:idleAutoPlay', v ? 1 : 0)"
      />
      <span class="auto-play-label">启用 pixi-live2d-display 自带自动待机（播放模型 Idle 组动作）</span>
    </div>
    <div class="form-tip" style="margin-top: 4px; margin-bottom: 10px;">
      开启=由引擎自动播放模型自带待机动作；关闭=按下方设置的待机表情/动作/间隔播放（未设置则静止）。
    </div>

    <div v-if="noWhitelist" class="value-empty">
      该模型暂无表情/动作白名单资产，无法配置待机行为（先上传含 exp3/motion3 的模型包）。
    </div>

    <template v-else>
      <el-form label-position="top" class="idle-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="待机表情">
              <el-select
                :model-value="idleExpressionList"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="选择待机时轮换的表情（可空）"
                class="idle-select"
                @update:model-value="(v) => emit('update:idleExpressionList', v)"
              >
                <el-option
                  v-for="id in expressionCandidates"
                  :key="id"
                  :label="candidateLabel(id, detail?.expressionList)"
                  :value="id"
                />
              </el-select>
              <div class="form-tip">从「表情白名单」中勾选；未选中则只待机动作</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="待机动作">
              <el-select
                :model-value="idleMotionList"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="选择待机时轮换的动作（可空）"
                class="idle-select"
                @update:model-value="(v) => emit('update:idleMotionList', v)"
              >
                <el-option
                  v-for="id in motionCandidates"
                  :key="id"
                  :label="candidateLabel(id, detail?.motionList)"
                  :value="id"
                />
              </el-select>
              <div class="form-tip">从「动作白名单」中勾选；未选中则只待机表情</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="待机间隔（秒）">
              <el-input-number
                :model-value="idleIntervalSeconds"
                :min="0"
                :max="3600"
                :step="1"
                controls-position="right"
                class="idle-interval"
                @update:model-value="(v) => emit('update:idleIntervalSeconds', v ?? 0)"
              />
              <div class="form-tip">每 N 秒随机挑一个已选动作/表情播放；0 = 关闭待机配置</div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="idle-actions">
        <el-button type="primary" size="small" :loading="saving" @click="emit('save')">保存待机配置</el-button>
        <el-button size="small" :disabled="saving" @click="emit('reset')">重置</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { assetLabel } from "@/admin/utils/live2dLabel";

/**
 * 待机配置卡片：编辑当前 Live2D 模型的
 * 待机表情 id 列表 / 待机动作 id 列表 / 待机间隔秒数（受控组件，v-model 三向绑定）。
 * 候选仅来自白名单（detail.expressionList/motionList 的 keySet），越权 id 不出现。
 * 「保存」把三个值经 emit('save') 交回父组件统一调 updateLive2dModel。
 */
const props = defineProps<{
  /** 当前待机表情 id 列表 */
  idleExpressionList?: string[];
  /** 当前待机动作 id 列表 */
  idleMotionList?: string[];
  /** 当前待机间隔（秒） */
  idleIntervalSeconds?: number;
  /** 是否启用库自带自动待机：1-是，0-否 */
  idleAutoPlay?: number;
  /** 模型详情（提供白名单候选 + 中文含义） */
  detail?: {
    expressionList?: Record<string, string> | null;
    motionList?: Record<string, string> | null;
  } | null;
  /** 保存中 */
  saving?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:idleExpressionList", v: string[]): void;
  (e: "update:idleMotionList", v: string[]): void;
  (e: "update:idleIntervalSeconds", v: number): void;
  (e: "update:idleAutoPlay", v: number): void;
  (e: "save"): void;
  (e: "reset"): void;
}>();

const expressionCandidates = computed(() => Object.keys(props.detail?.expressionList ?? {}));
const motionCandidates = computed(() => Object.keys(props.detail?.motionList ?? {}));
const noWhitelist = computed(() => !expressionCandidates.value.length && !motionCandidates.value.length);

  /** 卡片标题显示用，防止间隔/列表缺失时错误显示“已启用” */
  const configuredIdle = computed(
    () =>
      (props.idleAutoPlay ?? 1) === 0 &&
      (props.idleIntervalSeconds ?? 0) > 0 &&
      ((props.idleExpressionList?.length ?? 0) > 0 || (props.idleMotionList?.length ?? 0) > 0),
  );

  /** 待机是否启用（仅用于标签状态；库自动待机开关单独展示） */
  const enabled = computed(() => configuredIdle.value);

/** 候选 label：id（中文）；无中文时只显示 id */
function candidateLabel(id: string, map?: Record<string, string> | null): string {
  const label = assetLabel(map, id);
  return label ? `${id}（${label}）` : id;
}
</script>

<style scoped>
.idle-config-card {
  margin-top: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auto-play-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auto-play-label {
  font-size: 12.5px;
  color: #475569;
}

.idle-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 2px;
}

.idle-select {
  width: 100%;
}

.idle-interval {
  width: 180px;
}

.form-tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
}

.idle-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.value-empty {
  font-size: 12px;
  color: #cbd5e1;
  padding: 8px 0;
}
</style>