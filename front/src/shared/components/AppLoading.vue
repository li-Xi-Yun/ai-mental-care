<template>
  <div class="app-loading">
    <el-icon class="is-loading" :size="40">
      <Loading />
    </el-icon>
  </div>
</template>

<script setup lang="ts">
import { Loading } from "@element-plus/icons-vue";
</script>

<style scoped>
.app-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100vh;
}
</style>