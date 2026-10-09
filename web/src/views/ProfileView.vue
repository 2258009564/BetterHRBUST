<template>
  <div class="space-y-6">
    <!-- Unauthenticated State -->
    <UiCard v-if="!isLoggedIn" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="profile" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以查询您的真实学籍档案与异动记录</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- Student Campus Card Style Profile -->
      <UiCard>
        <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6">
          <div class="flex items-center gap-5">
            <!-- Avatar / Photo -->
            <div class="w-20 h-20 rounded-2xl bg-zinc-900 text-zinc-100 dark:bg-zinc-100 dark:text-zinc-900 flex items-center justify-center font-bold text-2xl shadow-md shrink-0 overflow-hidden">
              <img
                v-if="userProfile.photoUrl"
                :src="userProfile.photoUrl"
                alt="学生照片"
                class="w-full h-full object-cover"
              />
              <span v-else>{{ userProfile.realName ? userProfile.realName.slice(0, 1) : '学' }}</span>
            </div>
            <div>
              <div class="flex items-center gap-2.5">
                <h2 class="text-xl font-bold text-zinc-900 dark:text-zinc-100">{{ userProfile.realName || '哈理工同学' }}</h2>
                <UiBadge variant="success" dot size="sm">{{ userProfile.status || '在籍' }}</UiBadge>
              </div>
              <div class="text-xs text-zinc-500 mt-1 space-x-3">
                <span>学号: <span class="font-mono text-zinc-800 dark:text-zinc-200">{{ userProfile.studentNumber || studentNumber }}</span></span>
                <span v-if="userProfile.internalId">教务内部 ID: <span class="font-mono text-zinc-800 dark:text-zinc-200">{{ userProfile.internalId }}</span></span>
              </div>
              <div class="text-xs text-zinc-600 dark:text-zinc-400 mt-1">
                {{ userProfile.college || '未知学院' }} · {{ userProfile.major || '未知专业' }} <span v-if="userProfile.className">({{ userProfile.className }})</span>
              </div>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <!-- Sync Profile Button -->
            <UiButton
              variant="outline"
              size="sm"
              :loading="loading"
              @click="refreshProfile"
            >
              <template #prefix>
                <Icon name="refresh" customClass="w-3.5 h-3.5" />
              </template>
              同步档案
            </UiButton>

            <!-- Privacy Mask Switch Button -->
            <UiButton
              variant="outline"
              size="sm"
              @click="isMasked = !isMasked"
            >
              <template #prefix>
                <Icon :name="isMasked ? 'eye' : 'eye-off'" customClass="w-3.5 h-3.5" />
              </template>
              {{ isMasked ? '显示完整信息' : '脱敏隐私信息' }}
            </UiButton>
          </div>
        </div>
      </UiCard>

      <!-- Details Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- Academic Status Info -->
        <UiCard title="学籍详细档案">
          <div class="space-y-3 text-xs">
            <div class="flex justify-between py-1.5 border-b border-zinc-100 dark:border-zinc-800">
              <span class="text-zinc-500">培养层次 / 学生类别</span>
              <span class="font-medium text-zinc-900 dark:text-zinc-100">{{ userProfile.studentType || '普通本科' }}</span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-zinc-100 dark:border-zinc-800">
              <span class="text-zinc-500">入学年级</span>
              <span class="font-medium text-zinc-900 dark:text-zinc-100">{{ userProfile.grade ? String(userProfile.grade).trim().replace(/(?:\s*级)+$/, '') + '级' : '—' }}</span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-zinc-100 dark:border-zinc-800">
              <span class="text-zinc-500">专业方向</span>
              <span class="font-medium text-zinc-900 dark:text-zinc-100">{{ userProfile.direction || '默认方向' }}</span>
            </div>
            <div class="flex justify-between py-1.5">
              <span class="text-zinc-500">学籍状态</span>
              <span class="font-medium text-emerald-600 dark:text-emerald-400">{{ userProfile.status || '正常' }}</span>
            </div>
          </div>
        </UiCard>

        <!-- Sensitive Privacy Fields -->
        <UiCard title="联系方式与敏感凭证 (隐私脱敏保护)">
          <div class="space-y-3 text-xs">
            <div class="flex justify-between py-1.5 border-b border-zinc-100 dark:border-zinc-800">
              <span class="text-zinc-500">证件号码</span>
              <span class="font-mono text-zinc-900 dark:text-zinc-100">
                {{ formatIdCard(userProfile.idCard) }}
              </span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-zinc-100 dark:border-zinc-800">
              <span class="text-zinc-500">电子邮箱</span>
              <span class="text-zinc-900 dark:text-zinc-100">
                {{ formatEmail(userProfile.email) }}
              </span>
            </div>
            <div class="flex justify-between py-1.5 border-b border-zinc-100 dark:border-zinc-800">
              <span class="text-zinc-500">联系电话</span>
              <span class="font-mono text-zinc-900 dark:text-zinc-100">
                {{ formatPhone(userProfile.phone) }}
              </span>
            </div>
            <div class="flex justify-between py-1.5">
              <span class="text-zinc-500">通讯地址</span>
              <span class="text-zinc-900 dark:text-zinc-100 text-right max-w-xs truncate">
                {{ formatAddress(userProfile.address) }}
              </span>
            </div>
          </div>
        </UiCard>
      </div>

      <!-- Status Changes History (学籍异动记录) -->
      <UiCard title="学籍异动记录 (休学/复学/专业分流/降级)">
        <div v-if="!userProfile.changes || userProfile.changes.length === 0" class="text-center py-6 text-xs text-zinc-400">
          教务系统未记载异动记录，学籍正常
        </div>
        <div v-else class="space-y-4">
          <div
            v-for="(ch, idx) in userProfile.changes"
            :key="idx"
            class="flex items-start gap-4 text-xs"
          >
            <div class="w-2 h-2 rounded-full bg-zinc-900 dark:bg-zinc-100 mt-1 shrink-0" />
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <span class="font-bold text-zinc-900 dark:text-zinc-100">{{ ch.type }}</span>
                <span class="text-zinc-400 font-mono text-[11px]">{{ ch.date }}</span>
              </div>
              <div class="text-zinc-600 dark:text-zinc-400 mt-0.5">
                {{ ch.reason }} <span v-if="ch.remark">({{ ch.remark }})</span>
              </div>
            </div>
          </div>
        </div>
      </UiCard>
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useToast } from '@/composables/useToast.js';
import { academicApi } from '@/services/academic/api.js';

const { isLoggedIn, userProfile, studentNumber, openLoginModal } = useSession();
const { showToast } = useToast();

const isMasked = ref(true);
const loading = ref(false);

function formatIdCard(card) {
  if (!card) return '未登记';
  if (!isMasked.value) return card;
  if (card.length >= 8) {
    return card.slice(0, 6) + '********' + card.slice(-4);
  }
  return '******';
}

function formatEmail(mail) {
  if (!mail) return '未登记';
  if (!isMasked.value) return mail;
  const parts = mail.split('@');
  if (parts.length === 2) {
    return parts[0].slice(0, 2) + '***@' + parts[1];
  }
  return '***@hrbust.edu.cn';
}

function formatPhone(phone) {
  if (!phone) return '未登记';
  if (!isMasked.value) return phone;
  if (phone.length >= 7) {
    return phone.slice(0, 3) + '****' + phone.slice(-4);
  }
  return '138****0000';
}

function formatAddress(addr) {
  if (!addr) return '未登记';
  if (!isMasked.value) return addr;
  return addr.slice(0, 8) + '***';
}

async function refreshProfile() {
  loading.value = true;
  try {
    const info = await academicApi.getPersonalInfo();
    Object.assign(userProfile, info);
    showToast({ title: '档案已同步更新', type: 'success' });
  } catch (err) {
    showToast({ title: '档案拉取失败', message: err.message, type: 'danger' });
  } finally {
    loading.value = false;
  }
}
</script>
