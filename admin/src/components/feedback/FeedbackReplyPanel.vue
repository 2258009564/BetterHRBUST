<template>
  <section class="rounded-xl border border-zinc-200/80 dark:border-zinc-800 overflow-hidden bg-white dark:bg-[#1c1e24]">
    <header
      class="flex flex-wrap items-center gap-2 px-4 py-3 bg-zinc-50/80 dark:bg-[#1f2127] border-b border-zinc-200/80 dark:border-zinc-800"
    >
      <span class="inline-flex items-center gap-2 text-xs font-semibold text-zinc-800 dark:text-zinc-200">
        <Icon name="send" customClass="w-3.5 h-3.5" />
        回复该反馈
      </span>
      <span
        v-if="autoDetected"
        class="ml-auto inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-400"
      >
        <Icon name="check-circle" customClass="w-3 h-3 shrink-0" />
        已从联系方式识别到邮箱
      </span>
      <span v-else class="ml-auto text-[11px] text-zinc-400 dark:text-zinc-500">联系方式中未识别到邮箱，可手动填写收件人</span>
    </header>

    <div class="p-4 space-y-3.5">
      <p
        v-if="!mailAvailable"
        class="flex items-start gap-2 rounded-lg border border-amber-200/70 dark:border-amber-800/50 bg-amber-50/70 dark:bg-amber-950/30 px-3 py-2.5 text-[11px] leading-relaxed text-amber-700 dark:text-amber-300"
      >
        <Icon name="alert-triangle" customClass="w-3.5 h-3.5 mt-px shrink-0" />
        <span>服务端尚未配置 SMTP 邮件服务，暂时无法发送回复。请联系超级管理员补全 SMTP 相关环境变量后重启服务。</span>
      </p>

      <div>
        <label class="block text-[11px] font-medium text-zinc-500 dark:text-zinc-400 mb-1.5">收件邮箱</label>
        <UiInput
          v-model="recipient"
          placeholder="例如：student@example.com"
          :maxlength="maxRecipientLength"
          :disabled="!mailAvailable || sending"
        >
          <template #prefix>
            <Icon name="mail" customClass="w-3.5 h-3.5" />
          </template>
        </UiInput>
        <p class="mt-1.5 text-[11px] leading-relaxed" :class="recipientHintClass">{{ recipientHint }}</p>
      </div>

      <div v-if="templates.length">
        <p class="mb-1.5 flex items-center gap-1.5 text-[11px] font-medium text-zinc-500 dark:text-zinc-400">
          <Icon name="sparkles" customClass="w-3 h-3" />
          快捷文案
          <span class="text-zinc-400 dark:text-zinc-500 font-normal">点击追加到正文末尾，不会覆盖已写内容</span>
        </p>
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="template in templates"
            :key="template.label + template.content"
            type="button"
            :disabled="!mailAvailable || sending"
            class="px-2.5 py-1 rounded-full border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-[#1f2127] text-[11px] text-zinc-600 dark:text-zinc-300 transition-all duration-150 cursor-pointer hover:border-rose-300 dark:hover:border-rose-800/70 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50/60 dark:hover:bg-rose-950/20 active:scale-[0.96] disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100"
            @click="appendTemplate(template)"
          >
            {{ template.label }}
          </button>
        </div>
      </div>

      <div>
        <label class="block text-[11px] font-medium text-zinc-500 dark:text-zinc-400 mb-1.5">邮件标题</label>
        <UiInput
          v-model="subject"
          placeholder="例如：关于您反馈的成绩页导出建议"
          :maxlength="maxSubjectLength"
          :disabled="!mailAvailable || sending"
        />
      </div>

      <div>
        <label class="block text-[11px] font-medium text-zinc-500 dark:text-zinc-400 mb-1.5">邮件正文</label>
        <UiTextarea
          ref="textareaRef"
          v-model="body"
          :rows="7"
          :maxlength="maxBodyLength"
          :disabled="!mailAvailable || sending"
          :custom-class="flash ? 'ring-2 ring-rose-400/30 border-rose-300 dark:border-rose-800/70' : ''"
          placeholder="向提交者说明处理结果、感谢反馈，或请其补充复现步骤…"
        />
        <div class="mt-1.5 flex items-center justify-between gap-3 text-[11px] text-zinc-400 dark:text-zinc-500">
          <span v-if="senderPreview" class="truncate">
            发件人显示为
            <span class="font-medium text-zinc-600 dark:text-zinc-300">{{ senderPreview }}</span>
          </span>
          <span v-else class="truncate">发件人显示名由服务端配置</span>
          <span :class="body.length > maxBodyLength ? 'text-rose-500' : ''">{{ body.length }} / {{ maxBodyLength }}</span>
        </div>
      </div>

      <div class="flex items-center gap-2 pt-0.5">
        <UiButton
          variant="primary"
          size="sm"
          :loading="sending"
          :disabled="!canSend"
          @click="submit"
        >
          <template #prefix>
            <Icon v-if="!sending" name="send" customClass="w-3.5 h-3.5" />
          </template>
          发送回复
        </UiButton>
        <UiButton variant="ghost" size="sm" :disabled="sending || isBlank" @click="clearDraft">清空</UiButton>
        <span class="ml-auto text-[11px] text-zinc-400 dark:text-zinc-500">发送成功后自动记入回复历史</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiTextarea from '@/components/ui/UiTextarea.vue';
import Icon from '@/components/icons/Icon.vue';
import { adminApi, ErrorCode } from '@/services/api.js';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import { excerpt } from '@/utils/format.js';

const props = defineProps({
  // 当前选中的反馈详情（email 可选：识别到则自动填入收件人）
  feedback: {
    type: Object,
    required: true
  },
  templates: {
    type: Array,
    default: () => []
  },
  mailEnabled: {
    type: Boolean,
    default: false
  },
  // 服务端下发的发件人显示名前缀（未配置 SMTP 时为空）
  senderPrefix: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['sent']);

const { currentUser } = useAuth();
const { showToast } = useToast();

const maxSubjectLength = 120;
const maxBodyLength = 4000;
const maxRecipientLength = 128;

// 与服务端 service.ExtractEmail 同源的宽松校验：仅用于即时提示，最终以服务端校验为准
const emailPattern = /[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}/;

const recipient = ref('');
const subject = ref('');
const body = ref('');
const sending = ref(false);
const flash = ref(false);
const textareaRef = ref(null);
// 发送时若后端回报「未配置」，本会话内直接禁用表单，避免重复无效尝试
const mailRejected = ref(false);

let flashTimer;

/** 从联系方式中识别出的邮箱：仅用于自动填入与提示，管理员可改成任意邮箱 */
const detectedEmail = computed(() => props.feedback?.email || '');
const autoDetected = computed(() => detectedEmail.value !== '');
const mailAvailable = computed(() => props.mailEnabled && !mailRejected.value);
const isBlank = computed(() => !subject.value.trim() && !body.value.trim());
const recipientValid = computed(() => emailPattern.test(recipient.value.trim()));
const canSend = computed(
  () =>
    mailAvailable.value &&
    !sending.value &&
    recipientValid.value &&
    subject.value.trim() !== '' &&
    body.value.trim().length >= 2
);
const recipientHint = computed(() => {
  const value = recipient.value.trim();
  if (!value) return '该反馈的联系方式中没有可识别的邮箱，请手动填写收件邮箱';
  if (!recipientValid.value) return '邮箱格式不正确，请检查后再发送';
  if (value.toLowerCase() === detectedEmail.value.toLowerCase()) {
    return '已从联系方式中自动识别并填入，可直接修改为其他邮箱';
  }
  return '回复邮件将发送到该邮箱';
});
const recipientHintClass = computed(() => {
  const value = recipient.value.trim();
  if (!value) return 'text-amber-600 dark:text-amber-400';
  return recipientValid.value ? 'text-zinc-400 dark:text-zinc-500' : 'text-rose-500 dark:text-rose-400';
});
const senderPreview = computed(() => {
  if (!props.senderPrefix) return '';
  const name = currentUser.value?.nickname || currentUser.value?.username || '管理员';
  return `${props.senderPrefix}·${name}`;
});

watch(
  () => props.feedback?.id,
  () => {
    mailRejected.value = false;
    resetDraft();
  },
  { immediate: true }
);

function defaultSubject() {
  return `关于您在 BetterHRBUST 提交的反馈：${excerpt(props.feedback?.content, 18)}`;
}

function resetDraft() {
  recipient.value = detectedEmail.value;
  subject.value = defaultSubject();
  body.value = '您好，感谢您对 BetterHRBUST 的关注与反馈！\n\n';
}

/** 清空草稿：收件人回到自动识别结果，标题与正文交给管理员重写 */
function clearDraft() {
  recipient.value = detectedEmail.value;
  subject.value = '';
  body.value = '';
}

/** 追加而非覆盖：始终保持原有内容，新文案另起一段接到末尾 */
function appendTemplate(template) {
  if (!mailAvailable.value || sending.value) return;
  const addition = String(template?.content || '').trim();
  if (!addition) return;

  const current = body.value.replace(/\s+$/, '');
  body.value = current ? `${current}\n\n${addition}` : addition;
  focusBodyEnd();

  flash.value = true;
  window.clearTimeout(flashTimer);
  flashTimer = window.setTimeout(() => {
    flash.value = false;
  }, 360);
}

function focusBodyEnd() {
  nextTick(() => {
    const element = textareaRef.value?.$el?.querySelector('textarea');
    if (!element) return;
    element.focus();
    element.setSelectionRange(element.value.length, element.value.length);
  });
}

async function submit() {
  if (!canSend.value) return;
  sending.value = true;
  const target = recipient.value.trim();
  try {
    const data = await adminApi.replyFeedback(props.feedback.id, {
      to: target,
      subject: subject.value.trim(),
      body: body.value
    });
    emit('sent', data.item);
    showToast({ title: '回复邮件已发送', message: `收件人：${target}`, type: 'success' });
    resetDraft();
  } catch (error) {
    if (error?.code === ErrorCode.MAIL_NOT_CONFIGURED) {
      mailRejected.value = true;
    }
    showToast({ title: '发送失败', message: error?.message || '请稍后重试', type: 'danger' });
  } finally {
    sending.value = false;
  }
}
</script>
