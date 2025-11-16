<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from "vue";
import { type Stats } from "../../types/stats";
import StatCard from "../components/StatCard.vue";
import ServerTable from "../components/ServerTable.vue";

// WebSocket configuration
const wsUrl = ref("ws://localhost:8081");
let ws: WebSocket | null = null;

// State
const connectionStatus = ref<"connected" | "connecting" | "disconnected">(
  "disconnected"
);
const statsData = ref<Stats | null>(null);
const messageCount = ref(0);
const lastUpdateTime = ref("");
const autoScroll = ref(true);
const messageHistory = ref<Array<{ time: string; preview: string }>>([]);

// Refs
const jsonContainer = ref<HTMLElement | null>(null);

// Computed
const connectionStatusText = computed(() => {
  switch (connectionStatus.value) {
    case "connected":
      return "Connected";
    case "connecting":
      return "Connecting...";
    case "disconnected":
      return "Disconnected";
    default:
      return "Unknown";
  }
});

const formattedJson = computed(() => {
  if (!statsData.value) return "";
  return JSON.stringify(statsData.value, null, 2);
});

// WebSocket functions
const connect = () => {
  if (ws && ws.readyState === WebSocket.OPEN) {
    return;
  }

  connectionStatus.value = "connecting";
  ws = new WebSocket(wsUrl.value);

  ws.onopen = () => {
    console.log("WebSocket connected");
    connectionStatus.value = "connected";
  };

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      statsData.value = data as Stats;
      messageCount.value++;
      lastUpdateTime.value = new Date().toLocaleTimeString();

      // Add to history
      const preview = JSON.stringify(data).substring(0, 100) + "...";
      messageHistory.value.unshift({
        time: new Date().toLocaleTimeString(),
        preview,
      });

      // Keep only last 20 messages
      if (messageHistory.value.length > 20) {
        messageHistory.value = messageHistory.value.slice(0, 20);
      }

      // Auto scroll to bottom
      if (autoScroll.value && jsonContainer.value) {
        nextTick(() => {
          if (jsonContainer.value) {
            jsonContainer.value.scrollTop = jsonContainer.value.scrollHeight;
          }
        });
      }
    } catch (error) {
      console.error("Error parsing WebSocket message:", error);
    }
  };

  ws.onerror = (error) => {
    console.error("WebSocket error:", error);
    connectionStatus.value = "disconnected";
  };

  ws.onclose = () => {
    console.log("WebSocket disconnected");
    connectionStatus.value = "disconnected";

    // Auto-reconnect after 3 seconds
    setTimeout(() => {
      if (connectionStatus.value === "disconnected") {
        console.log("Attempting to reconnect...");
        connect();
      }
    }, 3000);
  };
};

const disconnect = () => {
  if (ws) {
    ws.close();
    ws = null;
  }
  connectionStatus.value = "disconnected";
};

const toggleAutoScroll = () => {
  autoScroll.value = !autoScroll.value;
};

const copyToClipboard = async () => {
  if (statsData.value) {
    try {
      await navigator.clipboard.writeText(formattedJson.value);
      // You could add a toast notification here
      console.log("Copied to clipboard");
    } catch (error) {
      console.error("Failed to copy:", error);
    }
  }
};

const formatNumber = (num: number | null | undefined): string => {
  if (num === undefined || num === null) return "N/A";
  return new Intl.NumberFormat("en", { notation: "compact" }).format(num);
};

const clearHistory = () => {
  messageHistory.value = [];
};

const isAddingServer = ref(false);
const AddServerHandler = () => {
  // send a get request to localhost:8080/add-server,
  isAddingServer.value = true;
  fetch("http://localhost:8080/add-server")
    .then((response) => {
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      return response.json();
    })

    .finally(() => {
      isAddingServer.value = false;
    });
};

// Lifecycle
onMounted(() => {
  connect();
});

onUnmounted(() => {
  disconnect();
});

const autoScale = ref(true);
</script>
<template>
  <div class="flex items-center justify-center" v-if="statsData">
    <div class="flex flex-col h-full py-16 max-w-fit space-y-16 w-full">
      <div class="flex flex-col justify-center items-center">
        <div class="w-fit flex flex-col gap-2">
          <div
            class="font-black text-4xl text-primary tracking-tight w-fit leading-none"
          >
            <div>CSE540</div>
            <div>Load Balancer Live Analytics</div>
          </div>
          <div class="flex justify-between w-full">
            <div class="font-semibold">
              <span> Port: </span>
              <span class="text-muted">
                {{ statsData?.loadBalancer.port }}
              </span>
            </div>
            <div class="font-semibold">
              <span> Virtual Nodes per Server </span>
              <span class="text-muted">
                {{ statsData?.loadBalancer.virtualNodesPerServer }}
              </span>
            </div>
          </div>
        </div>
      </div>
      <div class="flex flex-row gap-16">
        <div class="w-fit flex flex-col gap-4">
          <div class="text-2xl flex flex-row justify-center items-center">
            <span class="circle" />
            <span class="text-muted">Total Requests Handled:</span>
            <span class="font-bold ml-2">
              {{ formatNumber(statsData?.performance.totalRequests) }}
            </span>
          </div>
          <StatCard title="Hash Ring" :data="statsData?.hashRing!" />
          <StatCard title="Performance" :data="statsData?.performance!" />
        </div>
        <div class="flex flex-col flex-1">
          <div class="flex justify-between">
            <USwitch v-model="autoScale" class="mb-4" label="Auto Scaling" />
            <UButton
              label="Add new Server"
              icon="i-lucide-plus"
              size="sm"
              class="h-fit"
              :loading="isAddingServer"
              @click="AddServerHandler"
            />
          </div>
          <ServerTable :data="statsData?.servers.nodes!" />
        </div>
      </div>
    </div>
  </div>
  <div class="flex items-center justify-center h-full" v-else>
    <UIcon name="i-lucide-loader" size="64" class="animate-spin bg-primary" />
  </div>
</template>

<style scoped>
pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.circle {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background-color: var(--ui-primary); /* Tailwind's green-400 */
  position: relative;
  margin-right: 8px;
}
</style>
