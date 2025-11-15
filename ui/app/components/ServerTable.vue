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
const getLoadColor = (load: number): string => {
  if (load < 1.5) return "text-green-600 dark:text-green-400";
  if (load < 3.0) return "text-yellow-600 dark:text-yellow-400";
  return "text-red-600 dark:text-red-400";
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
    sticky
    :data="props.data"
    :columns="columns"
    class="flex-1 max-h-[600px]"
  />
</template>
