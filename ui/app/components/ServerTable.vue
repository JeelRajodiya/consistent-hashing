<script setup lang="ts">
import { h } from "vue";
import type { TableColumn } from "@nuxt/ui";

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
  if (load < 80) return "text-green-600 dark:text-green-400"; // Healthy - below 80% capacity
  if (load < 100) return "text-yellow-600 dark:text-yellow-400"; // Warning - approaching capacity
  return "text-red-600 dark:text-red-400"; // Critical - over capacity, should scale up
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
];
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
