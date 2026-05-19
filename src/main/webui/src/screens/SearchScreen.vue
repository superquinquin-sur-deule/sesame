<template>
  <div class="search-screen screen-enter">
    <h1 class="search-title">Rechercher un coopérateur</h1>

    <div class="search-box" @keydown="onKeyDown">
      <div class="search-icon">
        <Icon name="search" :size="28" :stroke="2.6" />
      </div>
      <input
        ref="inputRef"
        class="search-input"
        placeholder="Prénom, nom ou n° de coopérateur"
        :value="query"
        autocomplete="off"
        spellcheck="false"
        @input="onInput"
      />
      <button
        v-if="query"
        class="search-clear"
        aria-label="Effacer"
        @click="clear"
      >
        <Icon name="x" :size="20" :stroke="2.6" />
      </button>
    </div>

    <div v-if="loading" class="loading-line">Recherche…</div>

    <div v-else-if="query && results.length === 0" class="empty">
      <Icon name="search" :size="36" :stroke="2.2" />
      <h3 class="empty-title">Aucun résultat pour « {{ query }} »</h3>
    </div>

    <section v-else-if="results.length > 0">
      <div class="results-list" role="listbox">
        <ResultRow
          v-for="(m, i) in results"
          :key="m.id"
          :member="m"
          :query="query"
          :focused="i === focusedIdx"
          @select="$emit('select', m)"
          @focus="focusedIdx = i"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import Icon from "../components/Icon.vue";
import ResultRow from "../components/ResultRow.vue";
import { searchMembers, type MemberSummary } from "../api/members";

const props = defineProps<{ query: string }>();
const emit = defineEmits<{
  "update:query": [v: string];
  select: [m: MemberSummary];
}>();

const inputRef = ref<HTMLInputElement | null>(null);
const results = ref<MemberSummary[]>([]);
const focusedIdx = ref(0);
const loading = ref(false);
let abortCtrl: AbortController | null = null;
let debounceHandle: number | null = null;

onMounted(() => inputRef.value?.focus());

function onInput(e: Event) {
  const v = (e.target as HTMLInputElement).value;
  emit("update:query", v);
}

function clear() {
  emit("update:query", "");
  inputRef.value?.focus();
}

watch(
  () => props.query,
  (q) => {
    focusedIdx.value = 0;
    if (debounceHandle) window.clearTimeout(debounceHandle);
    if (abortCtrl) abortCtrl.abort();
    if (!q.trim()) {
      results.value = [];
      loading.value = false;
      return;
    }
    loading.value = true;
    debounceHandle = window.setTimeout(async () => {
      abortCtrl = new AbortController();
      try {
        results.value = await searchMembers(q.trim(), abortCtrl.signal);
      } catch (err) {
        if ((err as { name?: string }).name !== "AbortError") {
          results.value = [];
        }
      } finally {
        loading.value = false;
      }
    }, 180);
  },
  { immediate: true }
);

function onKeyDown(e: KeyboardEvent) {
  if (e.key === "Escape") {
    emit("update:query", "");
    e.preventDefault();
    return;
  }
  if (!results.value.length) return;
  if (e.key === "ArrowDown") {
    focusedIdx.value = Math.min(focusedIdx.value + 1, results.value.length - 1);
    e.preventDefault();
  } else if (e.key === "ArrowUp") {
    focusedIdx.value = Math.max(focusedIdx.value - 1, 0);
    e.preventDefault();
  } else if (e.key === "Enter") {
    const m = results.value[focusedIdx.value];
    if (m) emit("select", m);
    e.preventDefault();
  }
}
</script>
