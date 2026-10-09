<template>
  <svg
    :class="['shrink-0 fill-none stroke-current', customClass]"
    :viewBox="viewBox"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <!-- 四端平台图标（大图标） -->
    <g v-if="name === 'browser'">
      <rect x="2.5" y="4" width="19" height="16" rx="3" />
      <line x1="2.5" y1="9" x2="21.5" y2="9" />
      <circle cx="6" cy="6.5" r="0.55" fill="currentColor" stroke="none" />
      <circle cx="8.5" cy="6.5" r="0.55" fill="currentColor" stroke="none" />
      <circle cx="11" cy="6.5" r="0.55" fill="currentColor" stroke="none" />
    </g>

    <g v-else-if="name === 'monkey'">
      <circle cx="12" cy="13" r="6.4" />
      <circle cx="4.7" cy="10.6" r="2.6" />
      <circle cx="19.3" cy="10.6" r="2.6" />
      <circle cx="9.6" cy="12.2" r="0.6" fill="currentColor" stroke="none" />
      <circle cx="14.4" cy="12.2" r="0.6" fill="currentColor" stroke="none" />
      <path d="M9.4 16c1.6 1.05 3.6 1.05 5.2 0" />
    </g>

    <g v-else-if="name === 'windows'">
      <rect x="3" y="4" width="8" height="7" rx="1.4" />
      <rect x="13" y="4" width="8" height="7" rx="1.4" />
      <rect x="3" y="13" width="8" height="7" rx="1.4" />
      <rect x="13" y="13" width="8" height="7" rx="1.4" />
    </g>

    <g v-else-if="name === 'android'">
      <path d="M5 15.5a7 7 0 0 1 14 0z" />
      <line x1="10" y1="8.8" x2="8.2" y2="5.7" />
      <line x1="14" y1="8.8" x2="15.8" y2="5.7" />
      <circle cx="9.6" cy="12.6" r="0.6" fill="currentColor" stroke="none" />
      <circle cx="14.4" cy="12.6" r="0.6" fill="currentColor" stroke="none" />
    </g>

    <!-- 通用图标 -->
    <g v-else-if="name === 'download'">
      <path d="M21 15v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3" />
      <polyline points="7 10 12 15 17 10" />
      <line x1="12" y1="15" x2="12" y2="3" />
    </g>

    <g v-else-if="name === 'arrow-right'">
      <line x1="4" y1="12" x2="20" y2="12" />
      <polyline points="13 5 20 12 13 19" />
    </g>

    <g v-else-if="name === 'arrow-down'">
      <line x1="12" y1="4" x2="12" y2="20" />
      <polyline points="5 13 12 20 19 13" />
    </g>

    <!-- 未上线平台「敬请期待」用 -->
    <g v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="8.6" />
      <polyline points="12 7.2 12 12 15.6 14.2" />
    </g>

    <!-- 未识别/未发布平台：按钮锁定态用 -->
    <g v-else-if="name === 'lock'">
      <rect x="4.5" y="10.3" width="15" height="9.7" rx="2.6" />
      <path d="M8 10.3V7.6a4 4 0 0 1 8 0v2.7" />
      <circle cx="12" cy="15.1" r="0.7" fill="currentColor" stroke="none" />
    </g>

    <g v-else-if="name === 'check'">
      <polyline points="20 6 9 17 4 12" />
    </g>

    <g v-else-if="name === 'shield'">
      <path d="M12 2.5 4.5 5.5v6c0 5 3.2 8.6 7.5 10 4.3-1.4 7.5-5 7.5-10v-6z" />
      <polyline points="8.8 11.8 11.2 14.2 15.4 9.4" />
    </g>

    <g v-else-if="name === 'sparkles'">
      <path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3z" />
    </g>

    <!-- 品牌图标（填充风格） -->
    <g v-else-if="name === 'github'" fill="currentColor" stroke="none">
      <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
    </g>

    <!-- 兜底 -->
    <circle v-else cx="12" cy="12" r="4" fill="currentColor" stroke="none" />
  </svg>
</template>

<script setup>
// 精简内联 SVG 图标组件（零依赖，写法与 web/src/components/icons/Icon.vue 一致）。
// 四端大图标以 stroke 线性风格绘制，stroke-width 1.6 在大尺寸下更显精致。
defineProps({
  name: {
    type: String,
    required: true
  },
  customClass: {
    type: [String, Array, Object],
    default: 'w-5 h-5'
  },
  viewBox: {
    type: String,
    default: '0 0 24 24'
  }
})
</script>
