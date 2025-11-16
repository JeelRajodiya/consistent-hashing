<script setup lang="ts">
import { h, ref } from "vue";
import type { TableColumn } from "@nuxt/ui";
import { UButton } from "#components";

type Server = {
  id: string;
  address: string;
  active: boolean;
  uptime: number;
  uptimeFormatted: string;
  requestCount: number;
  requestsPerSecond: number;
  loadPercentage: number;
};

interface Props {
  data: Server[];
}

const props = defineProps<Props>();

// Function to get color based on load percentage
// Load percentage represents capacity utilization (0-100% = within capacity, >100% = overloaded)
const getLoadColor = (load: number): string => {
  if (load < 50) return "text-success "; // Healthy - below 50% capacity
  if (load < 80) return "text-warning "; // Warning - approaching capacity
  return "text-error"; // Critical - over capacity, should scale up
};

const columns: TableColumn<Server>[] = [
  {
    accessorKey: "id",
    header: "Server ID",
    cell: ({ row }) => row.getValue("id"),
  },
  {
    accessorKey: "address",
    header: "Address",
    cell: ({ row }) => row.getValue("address"),
  },
  {
    accessorKey: "requestCount",
    header: () => h("div", { class: "text-right" }, "Total Requests"),
    cell: ({ row }) => {
      const count = row.getValue("requestCount") as number;
      const formatted = new Intl.NumberFormat("en-US").format(count);
      return h("div", { class: "text-right font-medium" }, formatted);
    },
  },
  {
    accessorKey: "requestsPerSecond",
    header: () => h("div", { class: "text-right" }, "Requests/Second"),
    cell: ({ row }) => {
      const rps = row.getValue("requestsPerSecond") as number;
      return h("div", { class: "text-right font-medium" }, rps.toFixed(2));
    },
  },
  {
    accessorKey: "loadPercentage",
    header: () => h("div", { class: "text-right" }, "Load (%)"),
    cell: ({ row }) => {
      const load = row.getValue("loadPercentage") as number;
      const color = getLoadColor(load);
      return h(
        "div",
        { class: `text-right font-semibold ${color}` },
        `${load.toFixed(2)}%`
      );
    },
  },
  {
    accessorKey: "actions",
    header: "",
    cell: ({ row }) => {
      const serverId = row.original.id;
      return h(UButton, {
        icon: "i-lucide-trash",
        color: "error",
        variant: "ghost",
        loading: removingServerIds.value.has(serverId),
        onClick: () => removeServer(serverId),
        size: "sm",
      });
    },
  },
];

const removingServerIds = ref(new Set<string>());

const removeServer = async (serverId: string) => {
  if (removingServerIds.value.has(serverId)) return;

  removingServerIds.value.add(serverId);
  try {
    await new Promise((resolve) => setTimeout(resolve, 300));
    const response = await fetch(
      `http://localhost:8080/remove-server?id=${serverId}`
    );
    if (!response.ok) {
      throw new Error("Network response was not ok");
    }
    // The server list will update via the WebSocket connection, so no need to manually remove from the table.
  } catch (error) {
    console.error(`Failed to remove server ${serverId}:`, error);
    // Optionally, show a toast or notification to the user about the failure.
  } finally {
    removingServerIds.value.delete(serverId);
  }
};
</script>

<template>
  <UTable
    v-if="data && data.length > 0"
    sticky
    :data="props.data"
    :columns="columns"
    class="flex-1 max-h-[600px]"
  />
</template>
