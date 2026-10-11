<!-- 头像上传：展示头像 / 首字母占位，hover 遮罩点击上传，上传与校验逻辑由 useProfile 提供 -->
<template>
  <el-upload
    class="avatar-uploader"
    :show-file-list="false"
    accept="image/*"
    :http-request="handleUpload"
    :before-upload="beforeUpload"
  >
    <div class="avatar-wrap">
      <el-avatar v-if="avatarUrl" :src="avatarUrl" :size="92" shape="circle" class="avatar-img" />
      <div v-else class="avatar-initials">{{ avatarText }}</div>
      <div class="avatar-mask">
        <el-icon class="avatar-mask__icon" :size="18"><Camera /></el-icon>
        <span>更换头像</span>
      </div>
    </div>
  </el-upload>
</template>

<script setup lang="ts">
import { Camera } from "@element-plus/icons-vue";

defineProps<{
  /** 已解析的头像可访问 URL（空串时展示首字母） */
  avatarUrl: string;
  /** 头像占位首字母 */
  avatarText: string;
  /** 上传请求处理（业务逻辑在 useProfile.ts） */
  handleUpload: (options: { file: File }) => Promise<unknown>;
  /** 上传前校验（业务逻辑在 useProfile.ts） */
  beforeUpload: (file: File) => boolean;
}>();
</script>

<style scoped>
.avatar-uploader {
  display: block;
}

.avatar-wrap {
  position: relative;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  cursor: pointer;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  background: rgba(15, 23, 42, 0.55);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
}

.avatar-img,
.avatar-initials {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-initials {
  color: #fff;
  font-size: 38px;
  font-weight: 600;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.3);
}
</style>
