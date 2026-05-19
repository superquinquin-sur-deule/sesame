<template>
  <svg :viewBox="`0 0 100 100`" :width="size" :height="size" role="img" :aria-label="`Photo de ${firstName} ${lastName}`">
    <rect width="100" height="100" :fill="bg" />
    <circle cx="50" cy="42" r="22" :fill="accent" opacity="0.18" />
    <path d="M 6 100 Q 6 76 32 70 L 68 70 Q 94 76 94 100 Z" :fill="accent" opacity="0.18" />
    <text
      x="50" y="56"
      text-anchor="middle"
      font-family="Raleway, sans-serif"
      font-weight="800"
      font-size="36"
      letter-spacing="-1"
      fill="#3C312E"
    >{{ initials }}</text>
  </svg>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    firstName: string;
    lastName: string;
    size?: number;
  }>(),
  { size: 64 }
);

/* Derive a consistent hue from the full name so the placeholder colour
   feels personal but stable across renders. */
const hue = computed(() => {
  const s = `${props.firstName}${props.lastName}`;
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
  return h;
});
const bg = computed(() => `oklch(0.86 0.07 ${hue.value})`);
const accent = computed(() => `oklch(0.62 0.13 ${hue.value})`);
const initials = computed(() => {
  const f = (props.firstName || " ").charAt(0).toUpperCase();
  const l = (props.lastName || " ").charAt(0).toUpperCase();
  return `${f}${l}`;
});
</script>
