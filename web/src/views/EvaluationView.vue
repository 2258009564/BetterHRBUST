<template>
  <div class="space-y-6">
    <UiCard title="教学评价助手">
      <p class="text-sm text-zinc-600 dark:text-zinc-400">按你设置的选项和评语处理所选课程，逐门核对学校返回的完成状态。</p>
      <div class="flex flex-wrap gap-2 mt-4">
        <UiButton size="sm" :loading="loading" :disabled="running" @click="loadTasks">刷新评价列表</UiButton>
        <UiButton size="sm" variant="outline" :disabled="!selected.length || loading || running" @click="loadTemplate">读取问卷并配置</UiButton>
        <UiButton size="sm" variant="primary" :disabled="!template || !selected.length || running || loading" @click="review">检查配置并开始批量提交</UiButton>
        <UiButton v-if="running" size="sm" variant="outline" @click="stopRequested = true">处理完当前课程后停止</UiButton>
      </div>
      <p v-if="message" role="status" class="text-xs mt-3 text-zinc-600 dark:text-zinc-400">{{ message }}</p>
      <p v-if="error" role="alert" class="text-xs mt-3 text-rose-500">{{ error }}</p>
    </UiCard>
    <UiCard title="评价课程">
      <p v-if="!tasks.length && !loading" class="text-xs text-zinc-500">原站尚未返回评价记录，请确认处于评价开放期。</p>
      <div v-for="task in tasks" :key="task.key" class="flex gap-3 items-center py-3 border-b border-zinc-200 dark:border-zinc-800 text-sm">
        <input v-model="chosen" type="checkbox" :value="task.key" :disabled="running || !task.pending || !task.url" :aria-label="'选择 ' + task.course" />
        <span class="flex-1">{{ task.course }} · {{ task.teacher }}</span><span class="text-xs text-zinc-500">{{ progress[task.key] || task.status }}</span>
      </div>
    </UiCard>
    <UiCard v-if="template" title="统一评价配置">
      <p class="text-xs text-zinc-500 mb-4">以下为原问卷中的项目，配置用于本次所选课程。问卷结构不同的课程会停止处理。</p>
      <div class="space-y-4">
        <label v-for="(group, index) in template.groups" :key="group.name" class="block text-sm">
          {{ group.question }}
          <select v-model="ratings[index]" :disabled="running" :aria-label="'评价项目 ' + (index + 1)" class="block mt-1 w-full p-2 border rounded-lg bg-white dark:bg-zinc-900 border-zinc-200 dark:border-zinc-700">
            <option value="">请选择</option><option v-for="(option, optionIndex) in group.options" :key="optionIndex" :value="String(optionIndex)">{{ option.label }}</option>
          </select>
        </label>
        <label v-for="(comment, index) in template.comments" :key="comment.name" class="block text-sm">
          {{ comment.question }}
          <textarea v-model="comments[index]" :disabled="running" :aria-label="'文字评价 ' + (index + 1)" class="block mt-1 w-full p-2 border rounded-lg bg-white dark:bg-zinc-900 border-zinc-200 dark:border-zinc-700" rows="3" />
        </label>
      </div>
    </UiCard>
    <UiModal v-model="confirming" title="确认批量教学评价">
      <p class="text-sm mb-4">将向学校提交 {{ selected.length }} 门课程的教学评价，使用下面的配置。</p>
      <ul class="space-y-2 text-sm"><li v-for="(group, index) in template?.groups || []" :key="group.name">{{ group.question }}：{{ group.options[Number(ratings[index])]?.label }}</li></ul>
      <div v-for="(comment, index) in template?.comments || []" :key="comment.name" class="text-sm mt-3"><p>{{ comment.question }}</p><p class="whitespace-pre-wrap text-zinc-500">{{ comments[index] }}</p></div>
      <div class="flex gap-2 mt-5"><UiButton variant="primary" :disabled="running || loading" @click="startBatch">确认使用以上配置提交</UiButton><UiButton variant="outline" @click="confirming = false">返回修改</UiButton></div>
    </UiModal>
  </div>
</template>
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiModal from '@/components/ui/UiModal.vue';
import { useSession } from '@/composables/useSession.js';
import { getEvaluationTasks, getEvaluationForm, evaluationBody, runEvaluationBatch } from '@/services/academic/evaluation.js';
const { isLoggedIn } = useSession();
const tasks = ref([]), chosen = ref([]), template = ref(null), ratings = ref([]), comments = ref([]);
const loading = ref(false), running = ref(false), confirming = ref(false), stopRequested = ref(false);
const message = ref(''), error = ref(''), progress = ref({});
const selected = computed(() => tasks.value.filter(task => task.pending && task.url && chosen.value.includes(task.key)));
async function loadTasks() {
  if (!isLoggedIn.value) { error.value = '请先登录教务系统'; return; }
  loading.value = true; error.value = '';
  try { tasks.value = await getEvaluationTasks(); chosen.value = tasks.value.filter(task => task.pending && task.url).map(task => task.key); }
  catch (err) { error.value = err.message; }
  finally { loading.value = false; if (!running.value) progress.value = {}; }
}
async function loadTemplate() {
  if (!selected.value.length || loading.value || running.value) return;
  loading.value = true; error.value = '';
  try {
    template.value = await getEvaluationForm(selected.value[0]);
    ratings.value = template.value.groups.map(() => ''); comments.value = template.value.comments.map(() => '');
  } catch (err) { error.value = err.message; }
  finally { loading.value = false; }
}
function review() {
  if (loading.value || running.value) return;
  try { evaluationBody(template.value, { ratings: ratings.value, comments: comments.value }); error.value = ''; confirming.value = true; }
  catch (err) { error.value = err.message; }
}
async function startBatch() {
  if (running.value || loading.value) return;
  confirming.value = false; running.value = true; stopRequested.value = false; error.value = '';
  const batch = [...selected.value];
  const settings = { ratings: [...ratings.value], comments: [...comments.value] };
  try {
    const count = await runEvaluationBatch(batch, template.value, settings, { stopped: () => stopRequested.value,
      progress: (task, status) => { progress.value[task.key] = status; message.value = task.course + '：' + status; } });
    message.value = `已核对完成 ${count} 门课程。${stopRequested.value ? '其余课程已停止。' : '可返回成绩页重新同步。'}`;
  } catch (err) { error.value = err.message; message.value = '批量任务已停止，请刷新评价列表核对状态。'; }
  finally {
    running.value = false;
    const failure = error.value;
    await loadTasks();
    if (failure) error.value = failure;
  }
}
onMounted(loadTasks);
onBeforeUnmount(() => { stopRequested.value = true; });
</script>
