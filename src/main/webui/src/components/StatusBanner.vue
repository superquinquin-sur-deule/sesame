<template>
  <div :class="['status-banner', `is-${status}`]">
    <div class="status-banner-icon">
      <Icon :name="iconName" :size="28" :stroke="2.8" />
    </div>
    <div class="status-banner-body">
      <div class="status-banner-title">{{ label }}</div>
      <div v-if="reason" class="status-banner-sub">{{ reason }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import Icon from "./Icon.vue";
import type { MemberStatus } from "../api/model";

const props = defineProps<{ status: MemberStatus; reason?: string | null }>();

const labels: Record<MemberStatus, string> = {
  ok: "À jour",
  alert: "En alerte",
  suspended: "Suspendu",
  removed: "Désinscrit",
};
const icons: Record<MemberStatus, string> = {
  ok: "check",
  alert: "alert",
  suspended: "block",
  removed: "minus",
};
const label = computed(() => labels[props.status]);
const iconName = computed(() => icons[props.status]);
const reason = computed(() => props.reason ?? null);
</script>
