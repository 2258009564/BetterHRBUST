import { ref } from 'vue';

// 未读反馈数量：由反馈列表页在每次拉取后写入，供侧边栏角标读取
const unreadCount = ref(0);

export function setUnreadCount(value) {
  const number = Number(value);
  unreadCount.value = Number.isFinite(number) && number > 0 ? number : 0;
}

export function useFeedbackStats() {
  return { unreadCount };
}
