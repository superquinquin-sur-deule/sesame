<template>
  <div class="detail-screen screen-enter">
    <button class="back-btn" @click="$emit('back')">
      <Icon name="arrow-left" :size="16" :stroke="2.6" />
      Retour
    </button>

    <div v-if="loading" class="loading-line">Chargement de la fiche…</div>

    <div v-else-if="member" class="detail-card">
      <div class="detail-left">
        <button
          v-if="member.photo"
          type="button"
          class="detail-photo detail-photo-button"
          :title="`Agrandir la photo de ${member.firstName} ${member.lastName}`"
          @click="photoOpen = true"
        >
          <img
            :src="member.photo"
            :alt="`Photo de ${member.firstName} ${member.lastName}`"
            class="detail-photo-img"
          />
        </button>
        <div v-else class="detail-photo">
          <Avatar :first-name="member.firstName" :last-name="member.lastName" :size="240" />
        </div>
        <div class="detail-id-pill">
          N° <span class="detail-id-pill-num">{{ formatNumber(member.number) }}</span>
        </div>

        <button type="button" class="detail-capture-btn" @click="openCapture">
          <Icon name="camera" :size="16" :stroke="2.4" />
          {{ member.photo ? "Reprendre la photo" : "Prendre une photo" }}
        </button>
      </div>

      <div class="detail-right">
        <h1 class="detail-name">
          {{ member.firstName }}<br />{{ member.lastName.toUpperCase() }}
        </h1>

        <StatusBanner :status="member.status" :reason="member.statusReason" />

        <div class="detail-info">
          <div class="detail-info-item">
            <div class="detail-info-key">
              <Icon name="mail" :size="12" :stroke="2.4" /> Email
            </div>
            <div class="detail-info-val">{{ member.email || "—" }}</div>
          </div>

          <div class="detail-info-item">
            <div class="detail-info-key">
              <Icon name="calendar" :size="12" :stroke="2.4" /> Membre depuis
            </div>
            <div class="detail-info-val">{{ formatDate(member.joinedOn) }}</div>
          </div>

          <div class="detail-info-item span-2">
            <div class="detail-info-key">
              <Icon name="clock" :size="12" :stroke="2.4" /> Prochain créneau
            </div>
            <div class="detail-info-val">
              <template v-if="member.nextShift">
                <span style="text-transform: capitalize">{{ formatDay(member.nextShift.date) }}</span>
                · <b>{{ member.nextShift.time }}</b>
                <small v-if="member.nextShift.role || member.binome">
                  {{ member.nextShift.role || "" }}
                  <template v-if="member.binome">{{ member.nextShift.role ? " · " : "" }}créneau partagé en binôme</template>
                </small>
              </template>
              <span v-else style="color: var(--sqq-brown-soft)">—</span>
            </div>
          </div>

          <div v-if="member.binome" class="detail-info-item span-2 binome-item">
            <div class="detail-info-key">
              <Icon name="users" :size="12" :stroke="2.4" /> Binôme
            </div>
            <button
              type="button"
              class="binome-card"
              :title="`Ouvrir la fiche de ${member.binome.firstName} ${member.binome.lastName}`"
              @click="$emit('select', member.binome.id)"
            >
              <span class="binome-avatar">
                <Avatar :first-name="member.binome.firstName" :last-name="member.binome.lastName" :size="48" />
              </span>
              <span class="binome-info">
                <span class="binome-name">{{ member.binome.firstName }} {{ member.binome.lastName }}</span>
                <span class="binome-meta">
                  N° {{ formatNumber(member.binome.number) }} · même créneau de bénévolat
                </span>
              </span>
              <StatusPill :status="member.binome.status" />
              <span class="binome-chevron"><Icon name="chevron-right" :size="20" /></span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty">
      <h3 class="empty-title">Coopérateur·trice introuvable</h3>
    </div>

    <Teleport to="body">
      <div
        v-if="photoOpen && member?.photo"
        class="photo-modal-overlay"
        role="dialog"
        aria-modal="true"
        :aria-label="`Photo de ${member.firstName} ${member.lastName}`"
        @click="photoOpen = false"
      >
        <button
          type="button"
          class="photo-modal-close"
          aria-label="Fermer"
          @click="photoOpen = false"
        >
          <Icon name="x" :size="24" :stroke="2.6" />
        </button>
        <img
          :src="member.photo"
          :alt="`Photo de ${member.firstName} ${member.lastName}`"
          class="photo-modal-img"
          @click.stop
        />
      </div>
    </Teleport>

    <WebcamCapture
      v-if="captureOpen"
      :uploading="uploading"
      :error-message="captureError"
      @confirm="onCaptureConfirm"
      @cancel="closeCapture"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from "vue";
import Avatar from "../components/Avatar.vue";
import Icon from "../components/Icon.vue";
import StatusPill from "../components/StatusPill.vue";
import StatusBanner from "../components/StatusBanner.vue";
import WebcamCapture from "../components/WebcamCapture.vue";
import { getMember, uploadMemberPhoto, type MemberDetail } from "../api/members";
import { formatDate, formatDay, formatNumber } from "../utils/format";

const props = defineProps<{ memberId: number }>();
const emit = defineEmits<{ back: []; select: [id: number] }>();

const member = ref<MemberDetail | null>(null);
const loading = ref(false);
const photoOpen = ref(false);
const captureOpen = ref(false);
const uploading = ref(false);
const captureError = ref<string | null>(null);
let abortCtrl: AbortController | null = null;

async function load(id: number) {
  if (abortCtrl) abortCtrl.abort();
  abortCtrl = new AbortController();
  loading.value = true;
  try {
    member.value = await getMember(id, abortCtrl.signal);
  } catch (err) {
    if ((err as { name?: string }).name !== "AbortError") {
      member.value = null;
    }
  } finally {
    loading.value = false;
  }
}

watch(() => props.memberId, (id) => {
  photoOpen.value = false;
  captureOpen.value = false;
  uploading.value = false;
  captureError.value = null;
  load(id);
}, { immediate: true });

function openCapture() {
  captureError.value = null;
  captureOpen.value = true;
}

function closeCapture() {
  if (uploading.value) return;
  captureOpen.value = false;
  captureError.value = null;
}

async function onCaptureConfirm(dataUrl: string) {
  uploading.value = true;
  captureError.value = null;
  try {
    member.value = await uploadMemberPhoto(props.memberId, dataUrl);
    captureOpen.value = false;
  } catch {
    captureError.value = "Échec de l'enregistrement de la photo. Réessayez.";
  } finally {
    uploading.value = false;
  }
}

function onGlobalKey(e: KeyboardEvent) {
  if (e.key !== "Escape") return;
  // While the capture modal is open, let WebcamCapture own the Escape key.
  if (captureOpen.value) return;
  if (photoOpen.value) {
    photoOpen.value = false;
  } else {
    emit("back");
  }
  e.preventDefault();
}
onMounted(() => window.addEventListener("keydown", onGlobalKey));
onUnmounted(() => window.removeEventListener("keydown", onGlobalKey));
</script>
