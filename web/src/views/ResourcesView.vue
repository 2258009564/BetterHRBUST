<template>
  <div class="space-y-5">
    <UiCard title="资料查找">
      <p class="text-sm text-zinc-500">教务处公开资料目录：分类搜索，打开学校原文或附件。</p>
      <div class="flex flex-wrap gap-3 mt-4">
        <input v-model="query" aria-label="搜索资料" placeholder="搜索标题，例如：学生证、缓考、四六级" class="flex-1 min-w-56 p-3 border rounded-xl border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900" />
        <select v-model="category" aria-label="资料分类" class="p-3 border rounded-xl border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-900"><option value="">全部分类</option><option v-for="item in catalog.categories" :key="item.name">{{ item.name }}</option></select>
      </div>
      <p class="text-xs text-zinc-500 mt-4">共 {{ filtered.length }} 条结果 · 索引更新：{{ catalog.updatedAt.slice(0, 10) }} · <a :href="catalog.source" target="_blank" rel="noopener noreferrer" class="underline">教务处资料下载原站</a></p>
    </UiCard>
    <UiCard v-for="item in visible" :key="item.category + item.id" :title="item.title">
      <p class="text-xs text-zinc-500 mb-3">{{ item.category }} · {{ item.date || '发布日期未标注' }}</p>
      <a :href="item.url" target="_blank" rel="noopener noreferrer" class="text-sm underline">查看学校原文 ↗</a>
      <ul v-if="item.attachments.length" class="space-y-2 mt-3"><li v-for="file in item.attachments" :key="file.url"><a :href="file.url" target="_blank" rel="noopener noreferrer" class="text-sm text-blue-600 dark:text-blue-400">↓ {{ file.title || '附件' }}</a></li></ul>
      <p v-else class="text-xs text-zinc-500 mt-2">该条目未解析到直接附件，可在原文页查看完整内容。</p>
    </UiCard>
    <p v-if="!filtered.length" role="status" class="text-zinc-500">未找到匹配资料，请尝试其他标题关键词或分类。</p>
    <div v-if="filtered.length > 20" class="flex gap-4 items-center"><UiButton :disabled="page === 1" @click="page--">上一页</UiButton><span class="text-sm">{{ page }} / {{ Math.ceil(filtered.length / 20) }}</span><UiButton :disabled="page * 20 >= filtered.length" @click="page++">下一页</UiButton></div>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import catalog from '../../../shared/resources.json';
const query = ref(''), category = ref(''), page = ref(1);
const filtered = computed(() => catalog.items.filter(item => (!category.value || item.category === category.value) &&
  (!query.value.trim() || item.title.toLowerCase().includes(query.value.trim().toLowerCase()))));
const visible = computed(() => filtered.value.slice((page.value - 1) * 20, page.value * 20));
watch([query, category], () => { page.value = 1; });
</script>
