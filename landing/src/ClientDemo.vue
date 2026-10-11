<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
const props = defineProps({ view: { type: String, default: 'dashboard' }, compact: Boolean, dark: Boolean })
const root = ref(null), width = ref(600)
let observer
const scale = computed(() => width.value / 1200)
const source = computed(() => `${import.meta.env.BASE_URL}?client-preview=${props.view}&theme=${props.dark ? 'dark' : 'light'}`)
onMounted(() => { observer = new ResizeObserver(([entry]) => { width.value = entry.contentRect.width }); observer.observe(root.value) })
onBeforeUnmount(() => observer?.disconnect())
</script>
<template><div ref="root" class="client-demo" :data-view="view" aria-label="真实客户端界面，使用虚构示例数据"><iframe :src="source" :title="view + ' 客户端展示'" tabindex="-1" loading="lazy" :style="{ transform: `scale(${scale})` }" /></div></template>
