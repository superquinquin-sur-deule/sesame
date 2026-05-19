<template>
  <button
    type="button"
    :class="['result-row', { 'is-focused': focused }]"
    role="option"
    :aria-selected="focused"
    @click="$emit('select')"
    @mouseenter="$emit('focus')"
  >
    <span class="result-photo">
      <Avatar :first-name="member.firstName" :last-name="member.lastName" :size="64" />
    </span>
    <span class="result-info">
      <span class="result-name" v-html="highlightedName" />
      <span class="result-meta">
        <span>
          <span class="result-meta-key">N°</span>
          <span v-html="highlightedNumber" />
        </span>
        <span v-if="member.email">
          <span class="result-meta-key">Email</span>
          {{ member.email }}
        </span>
      </span>
    </span>
    <StatusPill :status="member.status" />
    <span class="result-chevron"><Icon name="chevron-right" :size="22" /></span>
  </button>
</template>

<script setup lang="ts">
import { computed } from "vue";
import Avatar from "./Avatar.vue";
import StatusPill from "./StatusPill.vue";
import Icon from "./Icon.vue";
import type { MemberSummary } from "../api/members";
import { formatNumber, normalize } from "../utils/format";

const props = defineProps<{
  member: MemberSummary;
  query: string;
  focused: boolean;
}>();

defineEmits<{ select: []; focus: [] }>();

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!)
  );
}

function highlight(text: string, q: string): string {
  if (!q) return escapeHtml(text);
  const n = normalize(text);
  const qn = normalize(q);
  const idx = n.indexOf(qn);
  if (idx === -1) return escapeHtml(text);
  return (
    escapeHtml(text.slice(0, idx)) +
    "<mark>" + escapeHtml(text.slice(idx, idx + qn.length)) + "</mark>" +
    escapeHtml(text.slice(idx + qn.length))
  );
}

const highlightedName = computed(() =>
  highlight(`${props.member.firstName} ${props.member.lastName}`, props.query)
);
const highlightedNumber = computed(() =>
  highlight(formatNumber(props.member.number), props.query)
);
</script>
