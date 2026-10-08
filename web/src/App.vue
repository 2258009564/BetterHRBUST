<template>
  <AppLayout
    :activeTab="activeTab"
    @update:activeTab="navigateTo($event)"
  >
    <Transition name="fade" mode="out-in">
      <component
        :is="currentViewComponent"
        @navigate="navigateTo($event)"
      />
    </Transition>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import AppLayout from '@/components/layout/AppLayout.vue';
import { useSession } from '@/composables/useSession.js';
import DashboardView from '@/views/DashboardView.vue';
import TimetableView from '@/views/TimetableView.vue';
import ScoreView from '@/views/ScoreView.vue';
import ExamView from '@/views/ExamView.vue';
import ProgramView from '@/views/ProgramView.vue';
import ClassroomView from '@/views/ClassroomView.vue';
import ProfileView from '@/views/ProfileView.vue';
import NoticeView from '@/views/NoticeView.vue';
import CourseView from '@/views/CourseView.vue';
import SettingsView from '@/views/SettingsView.vue';
import LoginView from '@/views/LoginView.vue';

const { checkAuth, activeTab, navigateTo } = useSession();

onMounted(() => {
  checkAuth();
});

const views = {
  dashboard: DashboardView,
  timetable: TimetableView,
  score: ScoreView,
  exam: ExamView,
  program: ProgramView,
  classroom: ClassroomView,
  profile: ProfileView,
  notice: NoticeView,
  course: CourseView,
  settings: SettingsView,
  login: LoginView
};

const currentViewComponent = computed(() => views[activeTab.value] || DashboardView);
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