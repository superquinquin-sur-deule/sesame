<template>
  <span :class="['status', `status-${status}`, size === 'lg' ? 'status-lg' : '']">
    <span class="status-dot" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { MemberStatus } from "../api/members";

const props = withDefaults(
  defineProps<{ status: MemberStatus; size?: "md" | "lg" }>(),
  { size: "md" }
);

const labels: Record<MemberStatus, string> = {
  ok: "À jour",
  alert: "En alerte",
  suspended: "Suspendu·e",
  removed: "Désinscrit·e",
};
const label = computed(() => labels[props.status]);
</script>
