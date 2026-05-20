<template>
  <div class="app" :data-screen-label="selectedId ? 'Détail coopérateur·trice' : 'Recherche'">
    <header class="topbar">
      <div class="topbar-band">
        <div class="topbar-inner">
          <div class="brand">
            <div class="brand-logo">SQ</div>
            <div>
              <div class="brand-title">SuperQuinquin</div>
              <div class="brand-sub">SÉSAME</div>
            </div>
          </div>
          <div class="topbar-spacer" />
          <div class="topbar-meta">
            <div class="topbar-meta-block">
              <span class="topbar-meta-label">{{ dateStr }}</span>
              <span class="topbar-meta-value">{{ timeStr }}</span>
            </div>
          </div>
        </div>
      </div>
      <div class="topbar-rule" />
    </header>

    <main class="main">
      <DetailScreen
        v-if="selectedId !== null"
        :member-id="selectedId"
        @back="selectedId = null"
        @select="(id) => (selectedId = id)"
      />
      <SearchScreen
        v-else
        :query="query"
        @update:query="(v) => (query = v)"
        @select="(m) => (selectedId = m.id)"
      />
    </main>

    <footer class="foot">
      <div class="foot-rule" />
      <div class="foot-inner">
        <div class="foot-meta">SuperQuinquin · Accueil</div>
        <div class="foot-kbd-list">
          <span class="foot-kbd"><kbd class="k">↑</kbd><kbd class="k">↓</kbd> Naviguer</span>
          <span class="foot-kbd"><kbd class="k">Entrée</kbd> Ouvrir</span>
          <span class="foot-kbd"><kbd class="k">Échap</kbd> Retour</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from "vue";
import SearchScreen from "./screens/SearchScreen.vue";
import DetailScreen from "./screens/DetailScreen.vue";

const query = ref("");
const selectedId = ref<number | null>(null);
const now = ref(new Date());

let timer: number | null = null;
onMounted(() => {
  timer = window.setInterval(() => (now.value = new Date()), 30 * 1000);
});
onUnmounted(() => {
  if (timer) window.clearInterval(timer);
});

const timeStr = computed(() =>
  now.value.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })
);
const dateStr = computed(() =>
  now.value.toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" })
);
</script>
