<template>
  <Teleport to="body">
    <div
      class="capture-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="Prendre une photo"
    >
      <button
        type="button"
        class="capture-close"
        aria-label="Fermer"
        :disabled="uploading"
        @click="cancel"
      >
        <Icon name="x" :size="24" :stroke="2.6" />
      </button>

      <div class="capture-stage">
        <video
          v-show="phase === 'streaming'"
          ref="videoRef"
          class="capture-video"
          autoplay
          playsinline
          muted
          @playing="ready = true"
          @loadedmetadata="ready = true"
        />

        <img
          v-if="phase === 'preview' && captured"
          :src="captured"
          class="capture-still"
          alt="Aperçu de la photo capturée"
        />

        <div v-if="phase === 'starting'" class="capture-msg">Démarrage de la caméra…</div>
        <div v-else-if="phase === 'denied'" class="capture-msg capture-msg-error">
          Accès à la caméra refusé.<br />Autorisez la caméra dans le navigateur puis réessayez.
        </div>
        <div v-else-if="phase === 'unsupported'" class="capture-msg capture-msg-error">
          Caméra indisponible.<br />Vérifiez qu'un périphérique est branché et que la page est servie en HTTPS ou sur localhost.
        </div>
        <div v-else-if="phase === 'error'" class="capture-msg capture-msg-error">
          Impossible d'accéder à la caméra.<br /><small>{{ camError }}</small>
        </div>

        <div v-if="uploading" class="capture-uploading">
          <span class="capture-spinner" /> Enregistrement…
        </div>
      </div>

      <p v-if="errorMessage && phase === 'preview'" class="capture-error-banner" role="alert">
        {{ errorMessage }}
      </p>

      <div class="capture-actions">
        <template v-if="phase === 'streaming'">
          <button type="button" class="capture-btn capture-btn-ghost" @click="cancel">Annuler</button>
          <button
            type="button"
            class="capture-btn capture-btn-primary"
            :disabled="!ready"
            @click="takePhoto"
          >
            <Icon name="camera" :size="22" :stroke="2.4" />
            {{ ready ? "Prendre la photo" : "Initialisation…" }}
          </button>
        </template>

        <template v-else-if="phase === 'preview'">
          <button
            type="button"
            class="capture-btn capture-btn-ghost"
            :disabled="uploading"
            @click="retake"
          >
            Reprendre
          </button>
          <button
            type="button"
            class="capture-btn capture-btn-primary"
            :disabled="uploading"
            @click="validate"
          >
            <Icon name="check" :size="22" :stroke="2.6" /> Valider
          </button>
        </template>

        <template v-else-if="phase === 'denied' || phase === 'error'">
          <button type="button" class="capture-btn capture-btn-ghost" @click="cancel">Fermer</button>
          <button type="button" class="capture-btn capture-btn-primary" @click="start">Réessayer</button>
        </template>

        <template v-else-if="phase === 'unsupported'">
          <button type="button" class="capture-btn capture-btn-ghost" @click="cancel">Fermer</button>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from "vue";
import Icon from "./Icon.vue";

type Phase = "starting" | "streaming" | "preview" | "denied" | "unsupported" | "error";

const props = defineProps<{ uploading?: boolean; errorMessage?: string | null }>();
const emit = defineEmits<{ confirm: [dataUrl: string]; cancel: [] }>();

const videoRef = ref<HTMLVideoElement | null>(null);
const phase = ref<Phase>("starting");
const captured = ref<string | null>(null);
const camError = ref("");
const ready = ref(false); // first frame available — gates the shutter button
let stream: MediaStream | null = null;
let disposed = false; // component unmounted — drop any late-resolving stream

const OUTPUT_SIZE = 512; // square JPEG, matches Odoo's square avatar

async function start() {
  captured.value = null;
  camError.value = "";
  ready.value = false;
  phase.value = "starting";

  if (!navigator.mediaDevices?.getUserMedia) {
    phase.value = "unsupported";
    return;
  }

  try {
    const acquired = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false,
    });
    // The component may have been torn down while the permission prompt was open.
    if (disposed) {
      acquired.getTracks().forEach((t) => t.stop());
      return;
    }
    stream = acquired;
    phase.value = "streaming";
    await nextTick();
    if (videoRef.value) {
      videoRef.value.srcObject = stream;
      try {
        await videoRef.value.play();
      } catch {
        /* autoplay attribute handles playback; ignore */
      }
    }
  } catch (err) {
    const name = (err as { name?: string }).name;
    if (name === "NotAllowedError" || name === "SecurityError") {
      phase.value = "denied";
    } else if (name === "NotFoundError" || name === "OverconstrainedError") {
      phase.value = "unsupported";
    } else {
      camError.value = name ?? String(err);
      phase.value = "error";
    }
  }
}

function takePhoto() {
  const video = videoRef.value;
  if (!video || !video.videoWidth) return;
  const side = Math.min(video.videoWidth, video.videoHeight);
  const sx = (video.videoWidth - side) / 2;
  const sy = (video.videoHeight - side) / 2;
  const canvas = document.createElement("canvas");
  canvas.width = OUTPUT_SIZE;
  canvas.height = OUTPUT_SIZE;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  // Draw the raw (un-mirrored) frame so the stored photo has the correct orientation.
  ctx.drawImage(video, sx, sy, side, side, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
  captured.value = canvas.toDataURL("image/jpeg", 0.85);
  phase.value = "preview";
}

function retake() {
  if (props.uploading) return;
  captured.value = null;
  phase.value = stream ? "streaming" : "starting";
  if (!stream) start();
}

function validate() {
  if (props.uploading || !captured.value) return;
  emit("confirm", captured.value);
}

function stopCamera() {
  if (stream) {
    stream.getTracks().forEach((t) => t.stop());
    stream = null;
  }
  if (videoRef.value) videoRef.value.srcObject = null;
}

function cancel() {
  if (props.uploading) return;
  stopCamera();
  emit("cancel");
}

function onKey(e: KeyboardEvent) {
  if (e.key === "Escape" && !props.uploading) {
    e.preventDefault();
    e.stopPropagation();
    cancel();
  }
}

onMounted(() => {
  window.addEventListener("keydown", onKey, true);
  start();
});
onUnmounted(() => {
  disposed = true;
  window.removeEventListener("keydown", onKey, true);
  stopCamera();
});
</script>
