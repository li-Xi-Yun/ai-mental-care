<template>
  <div class="admin-profile-page">
    <h2 class="page-title">个人资料</h2>

    <div class="profile-card">
      <div class="avatar-section">
        <div class="avatar">👤</div>
        <el-button size="small" text>更换头像</el-button>
      </div>

      <el-form label-width="100px" class="profile-form">
        <el-form-item label="用户名">
          <el-input model-value="superadmin" disabled />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input model-value="张三" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input model-value="13800000001" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input model-value="admin@example.com" />
        </el-form-item>
        <el-form-item label="角色">
          <el-input model-value="超级管理员" disabled />
        </el-form-item>
        <el-form-item>
          <el-button type="primary">保存修改</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="password-card">
      <h3 class="section-title">修改密码</h3>
      <el-form label-width="100px">
        <el-form-item label="当前密码">
          <el-input type="password" placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input type="password" placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input type="password" placeholder="请再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary">修改密码</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.admin-profile-page {
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  max-width: 600px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 20px;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}

.profile-card {
  margin-bottom: 24px;
}

.password-card {
  border-top: 1px solid #f0f0f0;
  padding-top: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 16px;
}
</style>