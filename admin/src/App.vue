<template>
  <!-- 启动期：用本地令牌静默校验一次会话 -->
  <div
    v-if="!authChecked"
    class="min-h-screen flex flex-col items-center justify-center gap-3 bg-[#f6f7f9] dark:bg-[#14161a] text-zinc-500"
  >
    <Icon name="refresh" customClass="w-5 h-5 animate-spin" />
    <span class="text-xs">正在校验登录状态…</span>
  </div>

  <LoginView v-else-if="!isAuthenticated" />

  <!-- 首次登录（或超管重置口令后）强制改密，改密前不渲染任何业务页面 -->
  <PasswordView v-else-if="mustChangePassword" forced />

  <AdminLayout
    v-else
    :activeTab="activeTab"
    @update:activeTab="navigateTo"
    @logout="handleLogout"
  >
    <Transition name="fade" mode="out-in">
      <component :is="currentView" @navigate="navigateTo" />
    </Transition>
  </AdminLayout>

  <UiToast />
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import Icon from '@/components/icons/Icon.vue';
import LoginView from '@/views/LoginView.vue';
import PasswordView from '@/views/PasswordView.vue';
import FeedbackView from '@/views/FeedbackView.vue';
import AdminsView from '@/views/AdminsView.vue';
import AdminLayout from '@/components/layout/AdminLayout.vue';
import UiToast from '@/components/ui/UiToast.vue';
import { useAuth } from '@/composables/useAuth.js';

const { isAuthenticated, mustChangePassword, isSuperAdmin, authChecked, verifySession, logout } = useAuth();

const activeTab = ref('feedback');

function navigateTo(tab) {
  activeTab.value = tab;
}

function handleLogout() {
  logout();
  activeTab.value = 'feedback';
}

// 非超级管理员没有管理员管理入口，切回反馈页
watch(isSuperAdmin, value => {
  if (!value && activeTab.value === 'admins') activeTab.value = 'feedback';
});

const views = {
  feedback: FeedbackView,
  admins: AdminsView,
  password: PasswordView
};

const currentView = computed(() => {
  if (activeTab.value === 'admins' && !isSuperAdmin.value) return FeedbackView;
  return views[activeTab.value] || FeedbackView;
});

onMounted(() => {
  verifySession();
});
</script>

<style>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
.fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
